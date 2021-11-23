# Kubernetes Operator for Enterprise Manager "Cloud Management Pack"

This is an example for a Kubernetes Operator
which can provision and delete Oracle Databases (Single Instance or Pluggable Databases) 
which are managed by Enterprise Manager's "Cloud Management Pack".
With Cloud Management Pack, Oracle Databases can be used to provide a Dataase-as-a-Service
through a complete self-service Portal and REST interface.
New Databases are scheduled among a list of available Servers or Container Databases,
based on availability of resources (CPU, RAM, Disk...).

This Operator uses the REST API of Cloud Management Pack to provision new Databases (tested with pluggable databases)
or delete them by specifying a Custom Resource, named "OracleCMP", where CMP stands for "Cloud Management Pack".

### Build

You can build the sample using `mvn package` this will produce a Docker image you can push to the registry 
of your choice. The JAR file is built using your local Maven and JDK and then copied into the Docker image.

### Deployment

1. Create a nice namespace to keep all deployments, secrets and later perhaps the custom resources:
   kubectl create namespace oracmp-operator
2. Deploy the CRD: `
   kubectl apply -f k8s/crd.yaml`
3. Edit and Deploy the secret used for accessing the Cloud Management Pack and for default admin user/password of new databases: 
   The secret must have the same namespace as the operator in the next step
   The secret contains the OEM base url, like https://192.168.12.12:7803 without any URIs, the are added by the operator on demand.
   The secret contains OEM user name and password. This muser must have rights to access the templates and workloads in the Cloud Maagemtn Pack, like sysman/welcome1
   'kubectl apply -f ssausersecret.yaml"
4. Deploy the operator: `kubectl apply -f k8s/operator.yaml`
   The operator YAML must reference the same namespace as the beforementioned secret, at best the same namespace as with 1)
   The referenced Docker image is a public one on docker.io, You could reference your own after building by Yourself.
   Be aware that You would need a docker registry secret for a custom/private container registry and an additional parameter "imagePullSecret" in the operator YAML.
5. Create Your own Oracle PDB's easily through simple YAMLs (a custom resource of the kind "OracleCMP") with just a few parameters.
   Look at the sample "mycmp.yaml", You only need to specify a zone and template name, a workload name and the name of the database. 
   You could also add a comment and department name. The operator will find out the URIs and numbers behind those names 
   and make the Cloud Management Pack create a database accordingly.
   The operator will also create a secret with admin username and password inside as well as a configMap with database name and the connect string 
   obtained from Cloud Management Pack. 
   Please wait a while (about 2 minutes) until the PDB is created. Then the ConfigMap will show the connect URL. 
   You could wait until the status of the custom resource has changed from "INITIALIZING" to "READY" for that. 
   If anything fails, the status of the new resource will show the error message coming from the cloud management pack, 
   like invalid template name, database name already in use and such. 
   When deleting the custom resource, the database will also get deleted by using the URI stored in the custom resource's status.
   .
