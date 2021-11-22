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
   The secret must have the same namepace as the operator in the next step
   The secret contains the OEM base url, like https://192.168.12.12:7803 without any URIs, the are added by the operator on demand.
   The secret contains OEM user name and password. This muser must have rights to access the templates and workloads in the Cloud Maagemtn Pack, like sysman/welcome1
   'kubectl apply -f ssausersecret.yaml"
4. Deploy the operator: `kubectl apply -f k8s/operator.yaml`
   The operator YAML must reference the same namespace as the beforementioned secret, at best the same namespace as with 1)
   The referenced Docker image is a public one on docker.io, You could reference your own after building by Yourself.
   Be aware that You would need a docker registry secret for a custom/private container registry and an additional parameter "imagePullSecret" in the operator YAML.

