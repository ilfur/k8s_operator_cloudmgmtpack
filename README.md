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

1. Deploy the CRD: `kubectl apply -f k8s/crd.yaml`
2. Deploy the secret used for accessing the Cloud Management Pack and for default admin user/password of new databases: 'kubectl apply -f ssausersecret.yaml"
3. Deploy the operator: `kubectl apply -f k8s/operator.yaml`
