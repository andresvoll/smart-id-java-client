package ee.sk.smartid;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import ee.sk.smartid.exception.*;
import ee.sk.smartid.rest.dao.NationalIdentity;
import org.glassfish.jersey.apache.connector.ApacheConnectorProvider;
import org.glassfish.jersey.client.ClientConfig;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;
import static ee.sk.smartid.SmartIdRestServiceStubs.stubErrorResponse;
import static ee.sk.smartid.SmartIdRestServiceStubs.stubForbiddenResponse;
import static ee.sk.smartid.SmartIdRestServiceStubs.stubNotFoundResponse;
import static ee.sk.smartid.SmartIdRestServiceStubs.stubRequestWithResponse;
import static ee.sk.smartid.SmartIdRestServiceStubs.stubSessionStatusWithState;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class SmartIdClientTest {

  @Rule
  public WireMockRule wireMockRule = new WireMockRule(18089);

  private SmartIdClient client;

  @Before
  public void setUp() throws Exception {
    client = new SmartIdClient();
    client.setRelyingPartyUUID("de305d54-75b4-431b-adb2-eb6b9e546014");
    client.setRelyingPartyName("BANK123");
    client.setHostUrl("http://localhost:18089");
    stubRequestWithResponse("/certificatechoice/pno/EE/31111111111", "requests/certificateChoiceRequest.json", "responses/certificateChoiceResponse.json");
    stubRequestWithResponse("/certificatechoice/document/PNOEE-31111111111", "requests/certificateChoiceRequest.json", "responses/certificateChoiceResponse.json");
    stubRequestWithResponse("/certificatechoice/document/PNOEE-31111111111", "requests/certificateChoiceRequestWithNonce.json", "responses/certificateChoiceResponse.json");
    stubRequestWithResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequest.json", "responses/signatureSessionResponse.json");
    stubRequestWithResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequestWithSha512.json", "responses/signatureSessionResponse.json");
    stubRequestWithResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequestWithNonce.json", "responses/signatureSessionResponse.json");
    stubRequestWithResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequestWithDisplayText.json", "responses/signatureSessionResponse.json");
    stubRequestWithResponse("/session/97f5058e-e308-4c83-ac14-7712b0eb9d86", "responses/sessionStatusForSuccessfulCertificateRequest.json");
    stubRequestWithResponse("/session/2c52caf4-13b0-41c4-bdc6-aa268403cc00", "responses/sessionStatusForSuccessfulSigningRequest.json");
    stubRequestWithResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequest.json", "responses/authenticationSessionResponse.json");
    stubRequestWithResponse("/authentication/pno/EE/31111111111", "requests/authenticationSessionRequest.json", "responses/authenticationSessionResponse.json");
    stubRequestWithResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequestWithNonce.json", "responses/authenticationSessionResponse.json");
    stubRequestWithResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequestWithDisplayText.json", "responses/authenticationSessionResponse.json");
    stubRequestWithResponse("/session/1dcc1600-29a6-4e95-a95c-d69b31febcfb", "responses/sessionStatusForSuccessfulAuthenticationRequest.json");
  }

  @Test
  public void getCertificateAndSign_fullExample() throws Exception {
    // Provide data bytes to be signed (Default hash type is SHA-512)
    SignableData dataToSign = new SignableData("Hello World!".getBytes());

    // Calculate verification code
    assertEquals("4664", dataToSign.calculateVerificationCode());

    // Get certificate and document number
    SmartIdCertificate certificateResponse = client
        .getCertificate()
        .withCountryCode("EE")
        .withNationalIdentityNumber("31111111111")
        .withCertificateLevel("ADVANCED")
        .fetch();

    X509Certificate x509Certificate = certificateResponse.getCertificate();
    String documentNumber = certificateResponse.getDocumentNumber();

    // Sign the data using the document number
    SmartIdSignature signature = client
        .createSignature()
        .withDocumentNumber(documentNumber)
        .withSignableData(dataToSign)
        .withCertificateLevel("ADVANCED")
        .sign();

    byte[] signatureValue = signature.getValue();
    String algorithmName = signature.getAlgorithmName(); // Returns "sha512WithRSAEncryption"

    assertValidSignatureCreated(signature);
  }

  @Test
  public void getCertificateAndSign_withExistingHash() throws Exception {
    SmartIdCertificate certificateResponse = client
        .getCertificate()
        .withCountryCode("EE")
        .withNationalIdentityNumber("31111111111")
        .withCertificateLevel("ADVANCED")
        .fetch();

    String documentNumber = certificateResponse.getDocumentNumber();

    SignableHash hashToSign = new SignableHash();
    hashToSign.setHashType(HashType.SHA256);
    hashToSign.setHashInBase64("0nbgC2fVdLVQFZJdBbmG7oPoElpCYsQMtrY0c0wKYRg=");

    SmartIdSignature signature = client
        .createSignature()
        .withDocumentNumber(documentNumber)
        .withSignableHash(hashToSign)
        .withCertificateLevel("ADVANCED")
        .sign();

    assertValidSignatureCreated(signature);
  }

  @Test
  public void getCertificateUsingNationalIdentity() throws Exception {
    NationalIdentity identity = new NationalIdentity("EE", "31111111111");
    SmartIdCertificate certificate = client
        .getCertificate()
        .withNationalIdentity(identity)
        .withCertificateLevel("ADVANCED")
        .fetch();

    assertCertificateResponseValid(certificate);
  }

  @Test
  public void getCertificateUsingDocumentNumber() throws Exception {
    SmartIdCertificate certificate = client
        .getCertificate()
        .withDocumentNumber("PNOEE-31111111111")
        .withCertificateLevel("ADVANCED")
        .fetch();

    assertCertificateResponseValid(certificate);
  }

  @Test
  public void getCertificateWithNonce() throws Exception {
    SmartIdCertificate certificate = client
        .getCertificate()
        .withDocumentNumber("PNOEE-31111111111")
        .withCertificateLevel("ADVANCED")
        .withNonce("zstOt2umlc")
        .fetch();

    assertCertificateResponseValid(certificate);
  }

  @Test
  public void sign() throws Exception {
    SignableHash hashToSign = new SignableHash();
    hashToSign.setHashType(HashType.SHA256);
    hashToSign.setHashInBase64("0nbgC2fVdLVQFZJdBbmG7oPoElpCYsQMtrY0c0wKYRg=");

    assertEquals("1796", hashToSign.calculateVerificationCode());

    SmartIdSignature signature = client
        .createSignature()
        .withDocumentNumber("PNOEE-31111111111")
        .withSignableHash(hashToSign)
        .withCertificateLevel("ADVANCED")
        .sign();

    assertValidSignatureCreated(signature);
  }

  @Test
  public void signWithNonce() throws Exception {
    SignableHash hashToSign = new SignableHash();
    hashToSign.setHashType(HashType.SHA256);
    hashToSign.setHashInBase64("0nbgC2fVdLVQFZJdBbmG7oPoElpCYsQMtrY0c0wKYRg=");

    assertEquals("1796", hashToSign.calculateVerificationCode());

    SmartIdSignature signature = client
        .createSignature()
        .withDocumentNumber("PNOEE-31111111111")
        .withSignableHash(hashToSign)
        .withCertificateLevel("ADVANCED")
        .withNonce("zstOt2umlc")
        .sign();

    assertValidSignatureCreated(signature);
  }

  @Test
  public void signWithDisplayText() throws Exception {
    SignableHash hashToSign = new SignableHash();
    hashToSign.setHashType(HashType.SHA256);
    hashToSign.setHashInBase64("0nbgC2fVdLVQFZJdBbmG7oPoElpCYsQMtrY0c0wKYRg=");

    assertEquals("1796", hashToSign.calculateVerificationCode());

    SmartIdSignature signature = client
        .createSignature()
        .withDocumentNumber("PNOEE-31111111111")
        .withSignableHash(hashToSign)
        .withCertificateLevel("ADVANCED")
        .withDisplayText("Authorize transfer of €10")
        .sign();

    assertValidSignatureCreated(signature);
  }

  @Test(expected = CertificateNotFoundException.class)
  public void getCertificate_whenUserAccountNotFound_shouldThrowException() throws Exception {
    stubNotFoundResponse("/certificatechoice/pno/EE/31111111111", "requests/certificateChoiceRequest.json");
    makeGetCertificateRequest();
  }

  @Test(expected = UserAccountNotFoundException.class)
  public void sign_whenUserAccountNotFound_shouldThrowException() throws Exception {
    stubNotFoundResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequest.json");
    makeCreateSignatureRequest();
  }

  @Test(expected = UserRefusedException.class)
  public void getCertificate_whenUserCancels_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/97f5058e-e308-4c83-ac14-7712b0eb9d86", "responses/sessionStatusWhenUserHasRefused.json");
    makeGetCertificateRequest();
  }

  @Test(expected = UserRefusedException.class)
  public void sign_whenUserCancels_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/2c52caf4-13b0-41c4-bdc6-aa268403cc00", "responses/sessionStatusWhenUserHasRefused.json");
    makeCreateSignatureRequest();
  }

  @Test(expected = SessionTimeoutException.class)
  public void getCertificate_whenTimeout_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/97f5058e-e308-4c83-ac14-7712b0eb9d86", "responses/sessionStatusWhenTimeout.json");
    makeGetCertificateRequest();
  }

  @Test(expected = SessionTimeoutException.class)
  public void sign_whenTimeout_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/2c52caf4-13b0-41c4-bdc6-aa268403cc00", "responses/sessionStatusWhenTimeout.json");
    makeCreateSignatureRequest();
  }

  @Test(expected = DocumentUnusableException.class)
  public void getCertificate_whenDocumentUnusable_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/97f5058e-e308-4c83-ac14-7712b0eb9d86", "responses/sessionStatusWhenDocumentUnusable.json");
    makeGetCertificateRequest();
  }

  @Test(expected = DocumentUnusableException.class)
  public void sign_whenDocumentUnusable_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/2c52caf4-13b0-41c4-bdc6-aa268403cc00", "responses/sessionStatusWhenDocumentUnusable.json");
    makeCreateSignatureRequest();
  }

  @Test(expected = RequestForbiddenException.class)
  public void getCertificate_whenRequestForbidden_shouldThrowException() throws Exception {
    stubForbiddenResponse("/certificatechoice/pno/EE/31111111111", "requests/certificateChoiceRequest.json");
    makeGetCertificateRequest();
  }

  @Test(expected = RequestForbiddenException.class)
  public void sign_whenRequestForbidden_shouldThrowException() throws Exception {
    stubForbiddenResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequest.json");
    makeCreateSignatureRequest();
  }

  @Test(expected = ClientNotSupportedException.class)
  public void getCertificate_whenClientSideAPIIsNotSupportedAnymore_shouldThrowException() throws Exception {
    stubErrorResponse("/certificatechoice/pno/EE/31111111111", "requests/certificateChoiceRequest.json", 480);
    makeGetCertificateRequest();
  }

  @Test(expected = ClientNotSupportedException.class)
  public void sign_whenClientSideAPIIsNotSupportedAnymore_shouldThrowException() throws Exception {
    stubErrorResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequest.json", 480);
    makeCreateSignatureRequest();
  }

  @Test(expected = ServerMaintenanceException.class)
  public void getCertificate_whenSystemUnderMaintenance_shouldThrowException() throws Exception {
    stubErrorResponse("/certificatechoice/pno/EE/31111111111", "requests/certificateChoiceRequest.json", 580);
    makeGetCertificateRequest();
  }

  @Test(expected = ServerMaintenanceException.class)
  public void sign_whenSystemUnderMaintenance_shouldThrowException() throws Exception {
    stubErrorResponse("/signature/document/PNOEE-31111111111", "requests/signatureSessionRequest.json", 580);
    makeCreateSignatureRequest();
  }

  @Test
  public void setPollingSleepTimeoutForSignatureCreation() throws Exception {
    stubSessionStatusWithState("2c52caf4-13b0-41c4-bdc6-aa268403cc00", "responses/sessionStatusRunning.json", STARTED, "COMPLETE");
    stubSessionStatusWithState("2c52caf4-13b0-41c4-bdc6-aa268403cc00", "responses/sessionStatusForSuccessfulSigningRequest.json", "COMPLETE", STARTED);
    client.setPollingSleepTimeout(TimeUnit.SECONDS, 2L);
    long duration = measureSigningDuration();
    assertTrue("Duration is " + duration, duration > 2000L);
    assertTrue("Duration is " + duration, duration < 3000L);
  }

  @Test
  public void setPollingSleepTimeoutForCertificateChoice() throws Exception {
    stubSessionStatusWithState("97f5058e-e308-4c83-ac14-7712b0eb9d86", "responses/sessionStatusRunning.json", STARTED, "COMPLETE");
    stubSessionStatusWithState("97f5058e-e308-4c83-ac14-7712b0eb9d86", "responses/sessionStatusForSuccessfulCertificateRequest.json", "COMPLETE", STARTED);
    client.setPollingSleepTimeout(TimeUnit.SECONDS, 2L);
    long duration = measureCertificateChoiceDuration();
    assertTrue("Duration is " + duration, duration > 2000L);
    assertTrue("Duration is " + duration, duration < 3000L);
  }

  @Test
  public void setSessionStatusResponseSocketTimeout() throws Exception {
    client.setSessionStatusResponseSocketOpenTime(TimeUnit.SECONDS, 10L);
    SmartIdSignature signature = createSignature();
    assertNotNull(signature);
    verify(getRequestedFor(urlEqualTo("/session/2c52caf4-13b0-41c4-bdc6-aa268403cc00?timeoutMs=10000")));
  }

  @Test
  public void authenticateUsingDocumentNumber() throws Exception {
    AuthenticationHash authenticationHash = new AuthenticationHash();
    authenticationHash.setHashInBase64("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==\"");
    authenticationHash.setHashType(HashType.SHA512);

    assertEquals("4430", authenticationHash.calculateVerificationCode());

    SmartIdAuthenticationResponse authenticationResponse = client
        .createAuthentication()
        .withDocumentNumber("PNOEE-31111111111")
        .withAuthenticationHash(authenticationHash)
        .withCertificateLevel("ADVANCED")
        .authenticate();

    assertAuthenticationResponseValid(authenticationResponse);
  }

  @Test
  public void authenticateUsingNationalIdentity() throws Exception {
    NationalIdentity identity = new NationalIdentity("EE", "31111111111");

    AuthenticationHash authenticationHash = new AuthenticationHash();
    authenticationHash.setHashInBase64("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==");
    authenticationHash.setHashType(HashType.SHA512);

    assertEquals("4430", authenticationHash.calculateVerificationCode());

    SmartIdAuthenticationResponse authenticationResponse = client
        .createAuthentication()
        .withNationalIdentity(identity)
        .withAuthenticationHash(authenticationHash)
        .withCertificateLevel("ADVANCED")
        .authenticate();

    assertAuthenticationResponseValid(authenticationResponse);
  }

  @Test
  public void authenticateWithNonce() throws Exception {
    AuthenticationHash authenticationHash = new AuthenticationHash();
    authenticationHash.setHashInBase64("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==\"");
    authenticationHash.setHashType(HashType.SHA512);

    assertEquals("4430", authenticationHash.calculateVerificationCode());

    SmartIdAuthenticationResponse authenticationResponse = client
        .createAuthentication()
        .withDocumentNumber("PNOEE-31111111111")
        .withAuthenticationHash(authenticationHash)
        .withCertificateLevel("ADVANCED")
        .withNonce("g9rp4kjca3")
        .authenticate();

    assertAuthenticationResponseValid(authenticationResponse);
  }

  @Test
  public void authenticateWithDisplayText() throws Exception {
    AuthenticationHash authenticationHash = new AuthenticationHash();
    authenticationHash.setHashInBase64("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==\"");
    authenticationHash.setHashType(HashType.SHA512);

    assertEquals("4430", authenticationHash.calculateVerificationCode());

    SmartIdAuthenticationResponse authenticationResponse = client
        .createAuthentication()
        .withDocumentNumber("PNOEE-31111111111")
        .withAuthenticationHash(authenticationHash)
        .withCertificateLevel("ADVANCED")
        .withDisplayText("Log into internet banking system")
        .authenticate();

    assertAuthenticationResponseValid(authenticationResponse);
  }

  @Test(expected = UserAccountNotFoundException.class)
  public void authenticate_whenUserAccountNotFound_shouldThrowException() throws Exception {
    stubNotFoundResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequest.json");
    makeAuthenticationRequest();
  }

  @Test(expected = UserRefusedException.class)
  public void authenticate_whenUserCancels_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/1dcc1600-29a6-4e95-a95c-d69b31febcfb", "responses/sessionStatusWhenUserHasRefused.json");
    makeAuthenticationRequest();
  }

  @Test(expected = SessionTimeoutException.class)
  public void authenticate_whenTimeout_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/1dcc1600-29a6-4e95-a95c-d69b31febcfb", "responses/sessionStatusWhenTimeout.json");
    makeAuthenticationRequest();
  }

  @Test(expected = DocumentUnusableException.class)
  public void authenticate_whenDocumentUnusable_shouldThrowException() throws Exception {
    stubRequestWithResponse("/session/1dcc1600-29a6-4e95-a95c-d69b31febcfb", "responses/sessionStatusWhenDocumentUnusable.json");
    makeAuthenticationRequest();
  }

  @Test(expected = RequestForbiddenException.class)
  public void authenticate_whenRequestForbidden_shouldThrowException() throws Exception {
    stubForbiddenResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequest.json");
    makeAuthenticationRequest();
  }

  @Test(expected = ClientNotSupportedException.class)
  public void authenticate_whenClientSideAPIIsNotSupportedAnymore_shouldThrowException() throws Exception {
    stubErrorResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequest.json", 480);
    makeAuthenticationRequest();
  }

  @Test(expected = ServerMaintenanceException.class)
  public void authenticate_whenSystemUnderMaintenance_shouldThrowException() throws Exception {
    stubErrorResponse("/authentication/document/PNOEE-31111111111", "requests/authenticationSessionRequest.json", 580);
    makeAuthenticationRequest();
  }

  @Test
  public void setPollingSleepTimeoutForAuthentication() throws Exception {
    stubSessionStatusWithState("1dcc1600-29a6-4e95-a95c-d69b31febcfb", "responses/sessionStatusRunning.json", STARTED, "COMPLETE");
    stubSessionStatusWithState("1dcc1600-29a6-4e95-a95c-d69b31febcfb", "responses/sessionStatusForSuccessfulAuthenticationRequest.json", "COMPLETE", STARTED);
    client.setPollingSleepTimeout(TimeUnit.SECONDS, 2L);
    long duration = measureAuthenticationDuration();
    assertTrue("Duration is " + duration, duration > 2000L);
    assertTrue("Duration is " + duration, duration < 3000L);
  }

  @Test
  public void verifyAuthentication_withNetworkConnectionConfigurationHavingCustomHeader() throws Exception {
    String headerName = "custom-header";
    String headerValue = "Hi!";

    Map<String, String> headersToAdd = new HashMap<>();
    headersToAdd.put(headerName, headerValue);
    ClientConfig clientConfig = getClientConfigWithCustomRequestHeaders(headersToAdd);
    client.setNetworkConnectionConfig(clientConfig);
    makeAuthenticationRequest();

    verify(postRequestedFor(urlEqualTo("/authentication/document/PNOEE-31111111111"))
        .withHeader(headerName, equalTo(headerValue)));
  }

  @Test
  public void verifySigning_withNetworkConnectionConfigurationHavingCustomHeader() throws Exception {
    String headerName = "custom-header";
    String headerValue = "Hello?!";

    Map<String, String> headers = new HashMap<>();
    headers.put(headerName, headerValue);
    ClientConfig clientConfig = getClientConfigWithCustomRequestHeaders(headers);
    client.setNetworkConnectionConfig(clientConfig);
    makeCreateSignatureRequest();

    verify(postRequestedFor(urlEqualTo("/signature/document/PNOEE-31111111111"))
        .withHeader(headerName, equalTo(headerValue)));
  }

  @Test
  public void verifyCertificateChoice_withNetworkConnectionConfigurationHavingCustomHeader() throws Exception {
    String headerName = "custom-header";
    String headerValue = "Man, come on..";

    Map<String, String> headers = new HashMap<>();
    headers.put(headerName, headerValue);
    ClientConfig clientConfig = getClientConfigWithCustomRequestHeaders(headers);
    client.setNetworkConnectionConfig(clientConfig);
    makeGetCertificateRequest();

    verify(postRequestedFor(urlEqualTo("/certificatechoice/pno/EE/31111111111"))
        .withHeader(headerName, equalTo(headerValue)));
  }

  private long measureSigningDuration() {
    long startTime = System.currentTimeMillis();
    SmartIdSignature signature = createSignature();
    long endTime = System.currentTimeMillis();
    assertNotNull(signature);
    return endTime - startTime;
  }

  private SmartIdSignature createSignature() {
    SignableHash hashToSign = new SignableHash();
    hashToSign.setHashType(HashType.SHA256);
    hashToSign.setHashInBase64("0nbgC2fVdLVQFZJdBbmG7oPoElpCYsQMtrY0c0wKYRg=");
    SmartIdSignature signature = client
        .createSignature()
        .withDocumentNumber("PNOEE-31111111111")
        .withSignableHash(hashToSign)
        .withCertificateLevel("ADVANCED")
        .sign();
    return signature;
  }

  private long measureAuthenticationDuration() {
    long startTime = System.currentTimeMillis();
    SmartIdAuthenticationResponse AuthenticationResponse = createAuthentication();
    long endTime = System.currentTimeMillis();
    assertNotNull(AuthenticationResponse);
    return endTime - startTime;
  }

  private SmartIdAuthenticationResponse createAuthentication() {
    AuthenticationHash authenticationHash = new AuthenticationHash();
    authenticationHash.setHashInBase64("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==");
    authenticationHash.setHashType(HashType.SHA512);

    SmartIdAuthenticationResponse AuthenticationResponse = client
        .createAuthentication()
        .withDocumentNumber("PNOEE-31111111111")
        .withAuthenticationHash(authenticationHash)
        .withCertificateLevel("ADVANCED")
        .authenticate();
    return AuthenticationResponse;
  }

  private long measureCertificateChoiceDuration() {
    long startTime = System.currentTimeMillis();
    SmartIdCertificate certificate = client
        .getCertificate()
        .withDocumentNumber("PNOEE-31111111111")
        .withCertificateLevel("ADVANCED")
        .fetch();
    long endTime = System.currentTimeMillis();
    assertNotNull(certificate);
    return endTime - startTime;
  }

  private void makeGetCertificateRequest() {
    client
        .getCertificate()
        .withNationalIdentity(new NationalIdentity("EE", "31111111111"))
        .withCertificateLevel("ADVANCED")
        .fetch();
  }

  private void makeCreateSignatureRequest() {
    SignableHash hashToSign = new SignableHash();
    hashToSign.setHashType(HashType.SHA256);
    hashToSign.setHashInBase64("0nbgC2fVdLVQFZJdBbmG7oPoElpCYsQMtrY0c0wKYRg=");

    client
        .createSignature()
        .withDocumentNumber("PNOEE-31111111111")
        .withSignableHash(hashToSign)
        .withCertificateLevel("ADVANCED")
        .sign();
  }

  private void makeAuthenticationRequest() {
    AuthenticationHash authenticationHash = new AuthenticationHash();
    authenticationHash.setHashInBase64("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==");
    authenticationHash.setHashType(HashType.SHA512);

    client
        .createAuthentication()
        .withDocumentNumber("PNOEE-31111111111")
        .withAuthenticationHash(authenticationHash)
        .withCertificateLevel("ADVANCED")
        .authenticate();
  }

  private ClientConfig getClientConfigWithCustomRequestHeaders(Map<String, String> headers) {
    ClientConfig clientConfig = new ClientConfig().connectorProvider(new ApacheConnectorProvider());
    clientConfig.register(new ClientRequestHeaderFilter(headers));
    return clientConfig;
  }

  private void assertCertificateResponseValid(SmartIdCertificate certificate) {
    assertNotNull(certificate);
    assertNotNull(certificate.getCertificate());
    X509Certificate cert = certificate.getCertificate();
    assertThat(cert.getSubjectDN().getName(), containsString("SERIALNUMBER=PNOEE-31111111111"));
    assertEquals("PNOEE-31111111111", certificate.getDocumentNumber());
    assertEquals("QUALIFIED", certificate.getCertificateLevel());
  }

  private void assertValidSignatureCreated(SmartIdSignature signature) {
    assertNotNull(signature);
    assertThat(signature.getValueInBase64(), startsWith("luvjsi1+1iLN9yfDFEh/BE8h"));
    assertEquals("sha256WithRSAEncryption", signature.getAlgorithmName());
  }

  private void assertAuthenticationResponseValid(SmartIdAuthenticationResponse authenticationResponse) {
    assertNotNull(authenticationResponse);
    assertEquals("K74MSLkafRuKZ1Ooucvh2xa4Q3nz+R/hFWIShN96SPHNcem+uQ6mFMe9kkJQqp5EaoZnJeaFpl310TmlzRgNyQ==", authenticationResponse.getSignedHashInBase64());
    assertEquals("OK", authenticationResponse.getEndResult());
    assertNotNull(authenticationResponse.getCertificate());
    assertThat(authenticationResponse.getSignatureValueInBase64(), startsWith("luvjsi1+1iLN9yfDFEh/BE8h"));
    assertEquals("sha256WithRSAEncryption", authenticationResponse.getAlgorithmName());
  }

}
