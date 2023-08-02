# Jenkins - Podman-in-Podman

## Prerequisitos

* Crear una SA con permisos de Editor de registro de Openshift
* Loguearse al Bastion OCP
* Luego loguearse a la API de Openshift con un usaurio clusteradmin (para poder hacer debug de cada worker)

```bash
oc login --token=  --server=https://api.URI:6443
```

* Entrar a todos los nodos para cronear login de la SA diariamente con el objetivo de que Jenkins siempre 
pueda disponer en cada nodo el DIND

```bash
oc debug node/ocp.........
chroot /host
```

Crear el cron en cada nodo para el login de la SA en la registry de Openshift

```bash
vi /etc/cron.d/login-sa.sh 
```
Contenido:
```bash
0 1 * * * root /etc/login-sa-dind.sh
```
Crear el archivo con el login de la SA en Openshift

```bash
vi /etc/login-sa-dind.sh

oc login --token= --server=https://api.URI:6443  --insecure-skip-tls-verify
oc registry login
```

Guardar archivo y dar permisos de ejecución

```bash
chmod +x /etc/login-sa-dind.sh
```

Repetir el proceso en todos los nodos workers
Nota: Si se reemplaza worker por uno nuevo se deberá rearmar el login anterior.


## Build de Imagen con Podman

El siguiente Dockerfile instala las herramientas necesarias para poder ejecutar
el contenedor de Podman dentro de Openshift (awscli & oc cli).
Esta imagen se subira al registro interno de OCP para que todos los nodos anteriormente logueados tengan la
imagen.

## Dockerfile

```bash
FROM default-route-openshift-image-registry.apps.ocp02-noprod.pro.edenor/jenkins-dind/podman:9.2-5

RUN dnf install -y git unzip gettext
RUN curl "https://awscli.amazonaws.com/awscli-exe-linux-x86_64.zip" -o "awscliv2.zip" && \
    unzip awscliv2.zip && \
    ./aws/install

RUN curl --retry 7 -Lo /tmp/client-tools.tar.gz "https://mirror.openshift.com/pub/openshift-v4/clients/oc/latest/linux/oc.tar.gz"

RUN tar zxf /tmp/client-tools.tar.gz -C /usr/local/bin oc \
    && rm /tmp/client-tools.tar.gz

```

## Build de Dockerfile y Taggeo de nombre de Imagen
```bash
 podman build --tag default-route-openshift-image-registry.apps.ocp02-noprod.pro.edenor/jenkins-dind/dind:oc412 -f ./Dockerfile
```

## Pusheo de Imagen al registro interno de OCP
```bash
 podman push default-route-openshift-image-registry.apps.ocp02-noprod.pro.edenor/jenkins-dind/dind:oc412

```
## Jenkins

## Pipeline

Crear un nuevo Pipeline en Jenkins:

![Nombre de Pipeline](/Documentacion/Imagenes/NombrePipeline.png "Nuevo Pipeline Jenkins")

Parametrizar el proyecto con los siguientes valores:


![Parametro Entorno](/Documentacion/Imagenes/Parametro01.png "Parametro Entorno")


![Parametro Nombre](/Documentacion/Imagenes/Parametro02.png "Parametro Nombre")


![Parametro Tag](/Documentacion/Imagenes/Parametro03.png "Parametro Tag")

![Parametro Proyecto](/Documentacion/Imagenes/Parametro04.png "Parametro Proyecto")

![Parametro Ticket](/Documentacion/Imagenes/Parametro05.png "Parametro Ticket")

Copiar todo el contenido del JenkinsFile al pipeline script

![Pipeline Script](/Documentacion/Imagenes/Jenkinsfile.png "Pipeline Script")


Guardar el pipeline.

El pipeline ya estará listo para la ejecución y despliegue.



[Repository](https://)