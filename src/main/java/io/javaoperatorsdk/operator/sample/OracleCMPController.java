package io.javaoperatorsdk.operator.sample;

import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.RollableScalableResource;
import io.fabric8.kubernetes.client.dsl.ServiceResource;
import io.fabric8.kubernetes.client.utils.Serialization;
import io.javaoperatorsdk.operator.api.Context;
import io.javaoperatorsdk.operator.api.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;
import java.net.HttpURLConnection;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyManagementException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.json.JSONArray;

@Controller
public class OracleCMPController implements ResourceController<OracleCMP> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubernetesClient;
    private Client httpClient = null;

    public OracleCMPController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
        log.info("EM URL: " + System.getProperty("em.rest.url"));
        log.info("EM USR: " + System.getProperty("ssa.usr"));
    }

    @Override
    public UpdateControl<OracleCMP> createOrUpdateResource(
            OracleCMP oraServer, Context<OracleCMP> context) {

        String ns = oraServer.getMetadata().getNamespace();

        Map<String, String> data = new HashMap<>();
        Map<String, String> leanData = new HashMap<>();
        data.put("em_rest_url", System.getProperty("em.rest.url"));
        data.put("ssa_user", System.getProperty("ssa.usr"));
        data.put("ssa_pwd", System.getProperty("ssa.pwd"));
        data.put("pdbaas_zone", oraServer.getSpec().getPdbaas_zone());
        data.put("pdbaas_template", oraServer.getSpec().getPdbaas_template());
        data.put("pdbaas_workload", oraServer.getSpec().getPdbaas_workload());
        data.put("pdbaas_name", oraServer.getSpec().getPdbaas_service());
        data.put("pdbaas_tbs", oraServer.getSpec().getPdbaas_tbs());
        data.put("department", oraServer.getSpec().getDepartment());
        data.put("comment", oraServer.getSpec().getComment());

        leanData.put("pdb_name", oraServer.getSpec().getPdbaas_service());
        leanData.put("pdb_conn", "n/a");

        Secret pdbDefaultsSecret
                = kubernetesClient
                        .secrets()
                        .inNamespace(ns)
                        .withName("ssausersecret")
                        .get();

        Map<String, String> pwds = pdbDefaultsSecret.getData();
        String s64usr = null;
        if (pwds.get("pdb_default_admin") != null) {
            byte[] b64 = java.util.Base64.getDecoder().decode(pwds.get("pdb_default_admin").getBytes());
            s64usr = new String(b64);
        } else {
            s64usr = "pdbadmin";
        }
        leanData.put("pdb_admin", s64usr);
        data.put("pdbaas_user", (pwds.get("pdb_default_admin") == null ? "pdbadmin" : pwds.get("pdb_default_admin")));
        data.put("pdbaas_pwd", (pwds.get("pdb_default_pwd") == null ? "welcome1" : pwds.get("pdb_default_pwd")));

        Secret theSecret
                = kubernetesClient
                        .secrets()
                        .inNamespace(ns)
                        .withName(secretName(oraServer))
                        .get();

        ConfigMap theConfigMap
                = kubernetesClient
                        .configMaps()
                        .inNamespace(ns)
                        .withName(configMapName(oraServer))
                        .get();

        String targetURI = "";
        String statusTxt = null;

        if (theSecret == null) {
            Map secretMap = new HashMap<>();
            //byte[] b64 = java.util.Base64.getEncoder().encode(data.get("pdbaas_user").getBytes());
            //secretMap.put("pdb_admin", new String(b64));
            //b64 = java.util.Base64.getEncoder().encode(data.get("pdbaas_pwd").getBytes());
            //secretMap.put("pdb_pwd", new String(b64));
            secretMap.put("pdb_admin", data.get("pdbaas_user"));
            secretMap.put("pdb_pwd", data.get("pdbaas_pwd"));

            theSecret
                    = new SecretBuilder()
                            .withMetadata(
                                    new ObjectMetaBuilder()
                                            .withName(secretName(oraServer))
                                            .withNamespace(ns)
                                            .build())
                            .withData(secretMap)
                            .build();
        }

        if (theConfigMap == null) {
            try {
                targetURI = this.createPDB(data);
                statusTxt = "INITIATED";
                theConfigMap
                        = new ConfigMapBuilder()
                                .withMetadata(
                                        new ObjectMetaBuilder()
                                                .withName(configMapName(oraServer))
                                                .withNamespace(ns)
                                                .build())
                                .withData(leanData)
                                .build();
                log.info("Creating or updating ConfigMap {} in {}", theConfigMap.getMetadata().getName(), ns);
                kubernetesClient.configMaps().inNamespace(ns).createOrReplace(theConfigMap);

                log.info("Creating or updating Secret {} in {}", theSecret.getMetadata().getName(), ns);
                kubernetesClient.secrets().inNamespace(ns).createOrReplace(theSecret);
            } catch (OracleCMPException cmpe) {
                statusTxt = cmpe.getMessage();
                log.error("Error during PDB creation: ", cmpe);
            }

        }

        OracleCMPStatus status = new OracleCMPStatus();
        status.setTargetUri(targetURI);
        status.setDbStatus(statusTxt);
        oraServer.setStatus(status);

        ExecutorService executorService = Executors.newFixedThreadPool(1);
        executorService.execute(() -> {
            pullStatusFromCMP(oraServer);
        });

        return UpdateControl.updateStatusSubResource(oraServer);
    }

    @Override
    public DeleteControl deleteResource(
            OracleCMP oraServer, io.javaoperatorsdk.operator.api.Context<OracleCMP> context) {
        log.info("Execution deleteResource for: {}", oraServer.getMetadata().getName());

        log.info("Deleting Secret {}", secretName(oraServer));
        Resource<Secret> theSecret
                = kubernetesClient
                        .secrets()
                        .inNamespace(oraServer.getMetadata().getNamespace())
                        .withName(secretName(oraServer));

        if (theSecret.get() != null) {
            theSecret.delete();
        }

        log.info("Deleting ConfigMap {}", configMapName(oraServer));
        Resource<ConfigMap> configMap
                = kubernetesClient
                        .configMaps()
                        .inNamespace(oraServer.getMetadata().getNamespace())
                        .withName(configMapName(oraServer));
        if (configMap.get() != null) {
            configMap.delete();
        }
        log.info("Dropping PDB {}", oraServer.getSpec().getPdbaas_service());
        dropPDB(oraServer);
        return DeleteControl.DEFAULT_DELETE;
    }

    private static String configMapName(OracleCMP oraServer) {
        return oraServer.getMetadata().getName() + "-config";
    }

    private static String secretName(OracleCMP oraServer) {
        return oraServer.getMetadata().getName() + "-secret";
    }

    private <T> T loadYaml(Class<T> clazz, String yaml) {
        try (InputStream is = getClass().getResourceAsStream(yaml)) {
            return Serialization.unmarshal(is, clazz);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot find yaml on classpath: " + yaml);
        }
    }

    private void pullStatusFromCMP(OracleCMP oraServer) {
        boolean done = false;
        httpClient = buildClientIgnoreAll();
        String conn = "";
        String version = "";
        while (!done) {
            try {
                Thread.currentThread().sleep(30000);
            } catch (InterruptedException ex) {

            }
            String authorizationHeaderValue = System.getProperty("ssa.usr").concat(":").concat(System.getProperty("ssa.pwd"));
            byte[] res = java.util.Base64.getEncoder().encode(authorizationHeaderValue.getBytes());
            authorizationHeaderValue = new String(res);
            OracleCMPStatus oldstat = oraServer.getStatus();
            String url = System.getProperty("em.rest.url").concat(oldstat.getTargetUri());
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
                    log.debug(resTxt);
                    JSONObject dataObject = new JSONObject(resTxt);
                    String pdbstate = dataObject.getJSONObject("resource_state").getString("state");
                    log.info("recent state of PDB: " + pdbstate);
                    if (pdbstate.equals("READY")) {
                        done = true;
                        conn = dataObject.getString("connect_string");
                        version = dataObject.getString("db_version");
                    }
                    OracleCMPStatus newstat = new OracleCMPStatus();
                    newstat.setDbStatus(pdbstate);
                    newstat.setTargetUri(oldstat.getTargetUri());
                    //UpdateControl.updateStatusSubResource(oraServer);
                    try {
                        Resource<OracleCMP> resource = kubernetesClient.customResources(OracleCMP.class)
                                                                       .inNamespace(oraServer.getMetadata().getNamespace())
                                                                       .withName(oraServer.getMetadata().getName());
                        oraServer = resource.get();
                        oraServer.setStatus(newstat);
                        oraServer = resource.updateStatus(oraServer);
                    } catch (Exception e) {
                        log.error(e.getMessage(), e);
                    }
                }
            }
        }
        ConfigMap theConfigMap
                = kubernetesClient
                        .configMaps()
                        .inNamespace(oraServer.getMetadata().getNamespace())
                        .withName(configMapName(oraServer))
                        .get();
        Map<String, String> data = theConfigMap.getData();
        if (data != null) {
            conn = conn.replace(System.getProperty("host.replace"), System.getProperty("host.replaceTo"));
            data.put("pdb_conn", conn);
            data.put("pdb_version", version);
        }
        theConfigMap.setData(data);
        kubernetesClient.configMaps().inNamespace(oraServer.getMetadata().getNamespace()).createOrReplace(theConfigMap);

    }

    private void dropPDB(OracleCMP oraServer) {
        httpClient = buildClientIgnoreAll();
        String authorizationHeaderValue = System.getProperty("ssa.usr").concat(":").concat(System.getProperty("ssa.pwd"));
        byte[] res = java.util.Base64.getEncoder().encode(authorizationHeaderValue.getBytes());
        authorizationHeaderValue = new String(res);
        if (oraServer.getStatus() != null) {
            if (oraServer.getStatus().getTargetUri() != null) {
                String url = System.getProperty("em.rest.url").concat(oraServer.getStatus().getTargetUri());
                WebTarget target = httpClient.target(url);
                log.debug("Calling DELETE on following URL: " + url);
                Invocation.Builder invocationBuilder = target
                        .request()
                        .header("Accept", "application/oracle.com.cloud.common.PluggableDbPlatformInstance+json")
                        .header("Authorization", "Basic " + authorizationHeaderValue);

                Response response = invocationBuilder.delete();
                int status = response.getStatus();
                if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                    if (response.hasEntity()) {
                        String resTxt = String.valueOf(response.readEntity(String.class));
                        log.debug(resTxt);
                    }
                } else {
                    log.error("oh-oh, HTTP request not successful with state " + status);
                }
            } else {
                log.error("Huh, OracleCMP targetURI is null or empty ! Doing nothing");
            }
        } else {
            log.error("Huh, OracleCMP status is null ! Doing nothing");
        }
    }

    private String createPDB(Map<String, String> data) throws OracleCMPException {

        httpClient = buildClientIgnoreAll();

        String authorizationHeaderValue = System.getProperty("ssa.usr").concat(":").concat(System.getProperty("ssa.pwd"));
        byte[] res = java.util.Base64.getEncoder().encode(authorizationHeaderValue.getBytes());
        authorizationHeaderValue = new String(res);
        JSONObject dataObject = null;
        String templateURI = null;
        String zoneURI = null;
        String url = System.getProperty("em.rest.url").concat("/em/cloud");
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
                log.debug(resTxt);
                dataObject = new JSONObject(resTxt);
                JSONArray arr = dataObject.getJSONObject("service_templates").getJSONArray("elements");
                Iterator it = arr.iterator();
                while (it.hasNext()) {
                    JSONObject obj = (JSONObject) it.next();
                    log.info("template found: ".concat(obj.getString("name")));
                    if (obj.getString("name").equals(data.get("pdbaas_template"))) {
                        templateURI = obj.getString("uri");
                    }
                }
                arr = dataObject.getJSONObject("zones").getJSONArray("elements");
                it = arr.iterator();
                while (it.hasNext()) {
                    JSONObject obj = (JSONObject) it.next();
                    log.info("zone found: ".concat(obj.getString("name")));
                    if (obj.getString("name").equals(data.get("pdbaas_zone"))) {
                        zoneURI = obj.getString("uri");
                    }
                }
            }
        } else {
            throw new OracleCMPException("Metadata Request failed due to HTTP error: " + status);
        }

        if (zoneURI == null) {
            throw new OracleCMPException("Cannot find Zone " + data.get("pdbaas_zone"));
        }
        if (templateURI == null) {
            throw new OracleCMPException("Cannot find Template " + data.get("pdbaas_template"));
        }

        byte[] b64 = java.util.Base64.getDecoder().decode(data.get("pdbaas_user").getBytes());
        String s64usr = new String(b64);
        b64 = java.util.Base64.getDecoder().decode(data.get("pdbaas_pwd").getBytes());
        String s64pwd = new String(b64);

        if (zoneURI != null && templateURI != null) {
            String payload = "{"
                    + "  \"zone\" : \"" + zoneURI + "\","
                    + "  \"name\" : \"PDB1_Request\","
                    + "  \"end_date\" : \"2032-11-20T17:20:00ZEurope/Berlin\", "
                    + "  \"params\" :     {"
                    + "      \"username\" : \"" + s64usr + "\","
                    + "      \"password\" : \"" + s64pwd + "\","
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
            log.debug("calling EM with the following url: " + url);
            log.debug("calling EM with the following payload :\n" + payload);
            target = httpClient.target(url);
            invocationBuilder = target
                    .request()
                    .header("Authorization", "Basic " + authorizationHeaderValue)
                    .accept("*/*");

            response = invocationBuilder.post(Entity.entity(payload, "application/oracle.com.cloud.common.PluggableDbPlatformInstance+json"));
            status = response.getStatus();
            log.debug("HTTP status : " + status);

            if (response.getStatusInfo().getFamily() == Response.Status.Family.SUCCESSFUL) {
                if (response.hasEntity()) {
                    String resTxt = String.valueOf(response.readEntity(String.class));
                    log.debug(resTxt);
                    dataObject = new JSONObject(resTxt);
                }
            } else {
                String resTxt = "";
                if (response.hasEntity()) {
                    resTxt = String.valueOf(response.readEntity(String.class));
                }
                throw new OracleCMPException("Create PDB Request failed with HTTP error: " + status + "\n" + resTxt);
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
