/**
 * Copyright (c) Codice Foundation
 *
 * <p>This is free software: you can redistribute it and/or modify it under the terms of the GNU
 * Lesser General Public License as published by the Free Software Foundation, either version 3 of
 * the License, or any later version.
 *
 * <p>This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details. A copy of the GNU Lesser General Public
 * License is distributed along with this program and can be found at
 * <http://www.gnu.org/licenses/lgpl.html>.
 */
package org.codice.alliance.nsili.test.itests;

import static com.jayway.restassured.RestAssured.delete;
import static com.jayway.restassured.RestAssured.given;
import static com.xebialabs.restito.builder.stub.StubHttp.whenHttp;
import static com.xebialabs.restito.builder.verify.VerifyHttp.verifyHttp;
import static com.xebialabs.restito.semantics.Condition.custom;
import static com.xebialabs.restito.semantics.Condition.method;
import static com.xebialabs.restito.semantics.Condition.url;
import static org.codice.alliance.nsili.client.SampleNsiliClient.getAttributeFromDag;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.xebialabs.restito.semantics.Action;
import com.xebialabs.restito.semantics.Condition;
import com.xebialabs.restito.server.StubServer;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Hashtable;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpStatus;
import org.codice.alliance.nsili.client.SampleNsiliClient;
import org.codice.alliance.nsili.common.NsiliConstants;
import org.codice.alliance.nsili.common.UCO.DAG;
import org.codice.alliance.nsili.test.itests.common.AbstractNsiliIntegrationTest;
import org.codice.ddf.cxf.client.ClientBuilderFactory;
import org.codice.ddf.test.common.annotations.AfterExam;
import org.codice.ddf.test.common.annotations.BeforeExam;
import org.glassfish.grizzly.http.Method;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.junit.PaxExam;
import org.ops4j.pax.exam.spi.reactors.ExamReactorStrategy;
import org.ops4j.pax.exam.spi.reactors.PerSuite;
import org.osgi.service.cm.Configuration;

@RunWith(PaxExam.class)
@ExamReactorStrategy(PerSuite.class)
public class NsiliEndpointTest extends AbstractNsiliIntegrationTest {

  private static final String CORBA_DEFAULT_PORT_PROPERTY =
      "org.codice.alliance.corba_default_port";

  private static final DynamicPort CORBA_DEFAULT_PORT =
      new DynamicPort(CORBA_DEFAULT_PORT_PROPERTY, 6);

  private static final DynamicPort RESTITO_STUB_SERVER_PORT = new DynamicPort(7);

  private static final String NSILI_FILE_URI_PATH = "/nsili/file";

  private static final DynamicUrl RESTITO_STUB_SERVER =
      new DynamicUrl("http://localhost:", RESTITO_STUB_SERVER_PORT, NSILI_FILE_URI_PATH);

  private static final DynamicUrl NSILI_ENDPOINT_IOR_URL =
      new DynamicUrl(SERVICE_ROOT, "/nsili/ior.txt");

  private static final String SAMPLE_NSILI_CLIENT_FEATURE_NAME = "sample-nsili-client";

  private static StubServer server;

  private static String ingestedProductId;

  @Override
  public String[] getDefaultRequiredApps() {
    String[] allianceApps = super.getDefaultRequiredApps();
    String[] nsiliApps = (String[]) Arrays.copyOf(allianceApps, allianceApps.length + 1);
    nsiliApps[allianceApps.length] = "nsili-app";
    return nsiliApps;
  }

  @BeforeExam
  public void beforeNsiliEndpointTest() throws Exception {
    try {
      waitForSystemReady();
      getSecurityPolicy().configureRestForGuest();
      waitForSystemReady();
      System.setProperty(CORBA_DEFAULT_PORT_PROPERTY, CORBA_DEFAULT_PORT.getPort());

      startHttpListener();
    } catch (Exception e) {
      LOGGER.error("Failed in @BeforeExam: ", e);
      fail("Failed in @BeforeExam: " + e.getMessage());
    }
  }

  @AfterExam
  public void afterNsiliEndpointTest() {
    deleteMetacard(ingestedProductId);

    if (server != null) {
      server.stop();
    }
  }

  @Before
  public void startSampleNsiliClientFeature() throws Exception {
    ingestedProductId =
        ingestRecord("nsili-test-itests/src/test/resources/alliance.png", "image/png");
    getServiceManager().startFeature(true, SAMPLE_NSILI_CLIENT_FEATURE_NAME);
  }

  @After
  public void stopSampleNsiliClientFeature() throws Exception {
    getServiceManager().stopFeature(true, SAMPLE_NSILI_CLIENT_FEATURE_NAME);
    clearCatalog();
  }

  @Test
  public void testStandingQueryMgr() throws Exception {
    ClientBuilderFactory clientBuilderFactory =
        getServiceManager().getService(ClientBuilderFactory.class);
    SampleNsiliClient sampleNsiliClient =
        new SampleNsiliClient(
            Integer.parseInt(RESTITO_STUB_SERVER_PORT.getPort()),
            NSILI_ENDPOINT_IOR_URL.getUrl(),
            null,
            clientBuilderFactory);

    sampleNsiliClient.testStandingQueryMgr();

    sampleNsiliClient.cleanup();
  }

  @Test
  public void testCatalogMgrGetHitCount() throws Exception {
    ClientBuilderFactory clientBuilderFactory =
        getServiceManager().getService(ClientBuilderFactory.class);
    SampleNsiliClient sampleNsiliClient =
        new SampleNsiliClient(
            Integer.parseInt(RESTITO_STUB_SERVER_PORT.getPort()),
            NSILI_ENDPOINT_IOR_URL.getUrl(),
            null,
            clientBuilderFactory);

    assertThat(sampleNsiliClient.getHitCount(), is(1));

    sampleNsiliClient.cleanup();
  }

  @Test
  public void testSubmitQuery() throws Exception {
    ClientBuilderFactory clientBuilderFactory =
        getServiceManager().getService(ClientBuilderFactory.class);
    SampleNsiliClient sampleNsiliClient =
        new SampleNsiliClient(
            Integer.parseInt(RESTITO_STUB_SERVER_PORT.getPort()),
            NSILI_ENDPOINT_IOR_URL.getUrl(),
            null,
            clientBuilderFactory);

    // CatalogMgr
    DAG[] results = sampleNsiliClient.submitQuery();
    assertThat(results.length, is(1));
    DAG result = results[0];
    assertThat(sampleNsiliClient.getProductIdFromDag(result), is(ingestedProductId));

    // ProductMgr
    DAG parametersDag = sampleNsiliClient.getParameters(result);
    assertThat(
        getAttributeFromDag(parametersDag, NsiliConstants.IDENTIFIER), is(ingestedProductId));
    assertThat(getAttributeFromDag(parametersDag, NsiliConstants.STATUS), is("NEW"));
    assertThat(
        sampleNsiliClient.getRelatedFileTypes(result),
        is(new String[] {NsiliConstants.THUMBNAIL_TYPE}));
    final String expectedThumbnailFilename = ingestedProductId + "-THUMBNAIL.jpg";
    assertThat(
        sampleNsiliClient.getRelatedFiles(result), is(new String[] {expectedThumbnailFilename}));
    verifyStubServerPutRequest(expectedThumbnailFilename, "image/jpeg");

    // OrderMgr
    String orderResultFilename = sampleNsiliClient.testOrder(result);
    // TODO test processing packageElements of the order response
    // https://codice.atlassian.net/browse/CAL-192
    verifyStubServerPutRequest(orderResultFilename, "application/x-tar");

    sampleNsiliClient.cleanup();
  }

  @Test
  public void testCatalogMgrViaCallback() throws Exception {
    ClientBuilderFactory clientBuilderFactory =
        getServiceManager().getService(ClientBuilderFactory.class);
    SampleNsiliClient sampleNsiliClient =
        new SampleNsiliClient(
            Integer.parseInt(RESTITO_STUB_SERVER_PORT.getPort()),
            NSILI_ENDPOINT_IOR_URL.getUrl(),
            null,
            clientBuilderFactory);

    sampleNsiliClient.testCallbackCatalogMgr();

    sampleNsiliClient.cleanup();
  }

  private void startHttpListener() {
    server = new StubServer(Integer.parseInt(RESTITO_STUB_SERVER_PORT.getPort())).run();
    whenHttp(server)
        .match(method(Method.PUT), Condition.startsWithUri(NSILI_FILE_URI_PATH))
        .then(Action.success());
  }

  private void verifyStubServerPutRequest(String expectedFilename, String expectedType) {
    verifyHttp(server)
        .once(
            method(Method.PUT),
            url(RESTITO_STUB_SERVER.getUrl() + "/" + expectedFilename),
            custom(call -> call.getContentType().equals(expectedType)));
  }

  private String ingestRecord(String fileName, String fileType)
      throws IOException, InterruptedException {
    InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName);
    byte[] fileBytes = IOUtils.toByteArray(inputStream);

    getServiceManager().waitForSourcesToBeAvailable(REST_PATH.getUrl());

    return given()
        .multiPart("file", fileName, fileBytes, fileType)
        .expect()
        .statusCode(HttpStatus.SC_CREATED)
        .when()
        .post(REST_PATH.getUrl())
        .getHeader("id");
  }

  private void deleteMetacard(String id) {
    delete(REST_PATH.getUrl() + id);
  }

  private void configureSecurityStsClient() throws IOException, InterruptedException {
    Configuration stsClientConfig =
        configAdmin.getConfiguration("ddf.security.sts.client.configuration.cfg", null);
    Dictionary<String, Object> properties = new Hashtable<>();

    properties.put(
        "address",
        DynamicUrl.SECURE_ROOT + HTTPS_PORT.getPort() + "/services/SecurityTokenService?wsdl");
    stsClientConfig.update(properties);
  }
}