/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package io.javaoperatorsdk.operator.sample;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.security.KeyManagementException;
import java.util.Iterator;
import java.util.Map;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.cert.X509Certificate;
import java.util.logging.Level;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author MPFEIFER
 */
public class PDBcreateTest {

    private final Logger log = LoggerFactory.getLogger(getClass());
    private Client httpClient = null;

    public static void main(String[] args) {
        PDBcreateTest test = new PDBcreateTest();
        Map<String, String> data = new java.util.HashMap<>();
        data.put("em_rest_url", "https://130.61.182.141:7803");
        //data.put("em_rest_url", "127.0.0.1:8080");
        data.put("ssa_user", "Marcel");
        data.put("ssa_pwd", "FordsonMajor58!");
        data.put("pdbaas_zone", "K8Szone");
        data.put("pdbaas_template", "K8Stemplate");
        data.put("pdbaas_workload", "DefaultWorkload");
        data.put("pdbaas_user", "pdbadmin");
        data.put("pdbaas_pwd", "welcome1");
        data.put("pdbaas_name", "k8spdb");
        data.put("pdbaas_tbs", "pdb_tbs1");
        data.put("department", "Development");
        data.put("comment", "Operator Testing");
        try {
            test.log.error(test.createPDB(data));
        } catch (OracleCMPException cmpe) {
            test.log.error(cmpe.getMessage());
        }
    }

    private String createPDB(Map<String, String> data) throws OracleCMPException {

        httpClient = buildClientIgnoreAll();

        String authorizationHeaderValue = data.get("ssa_user").concat(":").concat(data.get("ssa_pwd"));
        byte[] res = java.util.Base64.getEncoder().encode(authorizationHeaderValue.getBytes());
        authorizationHeaderValue = new String(res);
        JSONObject dataObject = null;
        String templateURI = null;
        String zoneURI = null;
        String url = data.get("em_rest_url").concat("/em/cloud");
        WebTarget target = httpClient.target(url);
        Invocation.Builder invocationBuilder = target
                .request()
                .header("Accept", "*/*")
                .header("Authorization", "Basic " + authorizationHeaderValue);

        Response response = invocationBuilder.get();
        int status = response.getStatus();
        if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
            if (response.hasEntity()) {
                String resTxt = String.valueOf(response.readEntity(String.class));
                log.error(resTxt);
                dataObject = new JSONObject(resTxt);
                JSONArray arr = dataObject.getJSONObject("service_templates").getJSONArray("elements");
                Iterator it = arr.iterator();
                while (it.hasNext()) {
                    JSONObject obj = (JSONObject) it.next();
                    log.error("template found: ".concat(obj.getString("name")));
                    if (obj.getString("name").equals(data.get("pdbaas_template"))) {
                        templateURI = obj.getString("uri");
                    }
                }
                arr = dataObject.getJSONObject("zones").getJSONArray("elements");
                it = arr.iterator();
                while (it.hasNext()) {
                    JSONObject obj = (JSONObject) it.next();
                    log.error("zone found: ".concat(obj.getString("name")));
                    if (obj.getString("name").equals(data.get("pdbaas_zone"))) {
                        zoneURI = obj.getString("uri");
                    }
                }
            }
        }
        else {
            throw new OracleCMPException("Metadata Request failed due to HTTP error: " + status);
        }

        if (zoneURI == null) {
            throw new OracleCMPException("Cannot find Zone " + data.get("pdbaas_zone"));
        }
        if (templateURI == null) {
            throw new OracleCMPException("Cannot find Template " + data.get("pdbaas_template"));
        }

        if (zoneURI != null && templateURI != null) {
            String payload = "{"
                    + "  \"zone\" : \"" + zoneURI + "\","
                    + "  \"name\" : \"PDB1_Request\","
                    + "  \"end_date\" : \"2032-11-20T17:20:00ZEurope/Berlin\", "
                    + "  \"params\" :     {"
                    + "      \"username\" : \"" + data.get("pdbaas_user") + "\","
                    + "      \"password\" : \"" + data.get("pdbaas_pwd") + "\","
                    + "      \"workload_name\" : \"" + data.get("pdbaas_workload") + "\","
                    + "      \"pdb_name\" : \"" + data.get("pdbaas_name") + "\","
                    + "      \"service_name\" : \"" + data.get("pdbaas_name") + "service\" ,"
                    + "      \"tablespaces\" : [ \"" + data.get("pdbaas_tbs") + "\" ] },"
                    + "      \"instance_target_properties\" : ["
                    + "        { \"name\": \"Department\", \"value\": \"" + data.get("department") + "\" },"
                    + "        { \"name\": \"Comment\", \"value\": \"" + data.get("comment") + "\" }"
                    + "      ]"
                    + "}";
            //Payload payload = new Payload(zoneURI, data.get("pdbaas_user"), data.get("pdbaas_pwd"), data.get("pdbaas_workload"), data.get("pdbaas_name"),
            //                              data.get("pdbaas_tbs"), data.get("department") ,data.get("comment"));

            url = data.get("em_rest_url").concat(templateURI);
            target = httpClient.target(url);
            invocationBuilder = target
                    .request()
                    .header("Authorization", "Basic " + authorizationHeaderValue)
                    .accept("*/*");

            response = invocationBuilder.post(Entity.entity(payload,"application/oracle.com.cloud.common.PluggableDbPlatformInstance+json"));
            status = response.getStatus();

            if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                if (response.hasEntity()) {
                    String resTxt = String.valueOf(response.readEntity(String.class));
                    log.error(resTxt);
                    dataObject = new JSONObject(resTxt);
                }
            } else {
                throw new OracleCMPException("Create PDB Request failed due to HTTP error: " + status);
            }

        };
        return dataObject.getString("uri");

    }

    public Client buildClientIgnoreAll() {
        SSLContext sc = null;
        TrustManager[] noopTrustManager = new TrustManager[]{
            new X509TrustManager() {

                @Override
                public X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                @Override
                public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }

                @Override
                public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
                }
            }
        };
        try {
            sc = SSLContext.getInstance("ssl");
        } catch (Exception ex) {
        }
        try {
            sc.init(null, noopTrustManager, null);
        } catch (KeyManagementException ex) {
            java.util.logging.Logger.getLogger(PDBcreateTest.class.getName()).log(Level.SEVERE, null, ex);
        }
        // Create all-trusting host name verifier
        HostnameVerifier allHostsValid = new HostnameVerifier() {
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };
        return ClientBuilder.newBuilder()
                .sslContext(sc)
                .hostnameVerifier(allHostsValid)
                .build();
    }
}
