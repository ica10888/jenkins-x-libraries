# 如何使用

## 1. 安装 jenkins X
1. 使用 jx 指令 安装 jenkins X 在 kubernetes 集群上

``` bash

jx install --provider=kubernetes \
--skip-ingress \
--external-ip=172.16.20.63 \
--domain=172.16.20.63.nip.io \
--no-tiller

```


  * 172.16.20.63 为你的宿主机ip
    
2. 国内使用会拉不到google的镜像 ，用 `kubectl edit -n jx xxxxxxx`修改 namaspace jx 中的镜像
    * anjia0532/google-containers.heapster
    * anjia0532/google-containers.addon-resizer


3. 修改 docker 的守护进程,默认通过 https ，改成 http 。
  * `vi /etc/docker/daemon.json`
  * 添加一行 ` "insecure-registries": ["docker-registry.jx.172.16.20.63.nip.io:80","0.0.0.0:5000","10.68.22.54:5000"] ` 
  * 其中包含有 jenkins 主域名 ， 0.0.0.0 ，kubernetes service jenkins-x-docker-registry 
  * 重启守护进程 `service docker restart ` 
  * **kubernetes 集群的每一个 master 和 node 节点都要这样操作**


4. jx 默认会从maven中央仓库拉取依赖包，对于国内用户来说相当慢，且很可能拉取不到依赖包，需要修改 maven 配置到你的本地仓库 
首先写好配置


``` xml
<settings>

    <!-- sets the local maven repository outside of the ~/.m2 folder for easier mounting of secrets and repo -->
    <localRepository>${user.home}/.mvnrepository</localRepository>
    <!-- lets disable the download progress indicator that fills up logs -->
    <interactiveMode>false</interactiveMode>
    <mirrors>
        <mirror>
          <id>nexus-central</id>
          <mirrorOf>*,!nexus-public,!releases,!snapshots</mirrorOf>
          <url>http://你的本地仓库:8081/repository/maven-central/</url>
        </mirror>
<!--    <mirror>
          <id>nexus-public</id>
          <mirrorOf>nexus-public</mirrorOf>
          <url>http://你的本地仓库:8081/repository/maven-public/</url>
        </mirror>
        <mirror>
          <id>releases</id>
          <mirrorOf>releases</mirrorOf>
          <url>http://你的本地仓库:8081/repository/maven-releases/</url>
        </mirror>
        <mirror>
          <id>snapshots</id>
          <mirrorOf>snapshots</mirrorOf>
          <url>http://你的本地仓库:8081/repository/maven-snapshots/</url>
        </mirror> -->
    </mirrors>
    <servers>
        <server>
        <id>local-nexus</id>
        <username>admin</username>
        <password>admin123</password>
    </server>
    <server>
        <id>oss-sonatype-staging</id>
        <username></username>
        <password></password>
        </server>
    </servers>
    <profiles>
        <profile>
            <id>nexus</id>
            <properties>
                <altDeploymentRepository>local-nexus::default::http://你的本地仓库:8081/repository/maven-snapshots/</altDeploymentRepository>
                <altReleaseDeploymentRepository>local-nexus::default::http://你的本地仓库:8081/repository/maven-releases/</altReleaseDeploymentRepository>
                <altSnapshotDeploymentRepository>local-nexus::default::http://你的本地仓库:8081/repository/maven-snapshots/</altSnapshotDeploymentRepository>
            </properties>
        </profile>
        <profile>
            <id>release</id>
            <properties>
                <gpg.executable>gpg</gpg.executable>
                <gpg.passphrase>mysecretpassphrase</gpg.passphrase>
            </properties>
        </profile>
    </profiles>
    <activeProfiles>
        <!--make the profile active all the time -->
        <activeProfile>nexus</activeProfile>
    </activeProfiles>


```



  * 然后使用 base64 加密
  * 设置 maven 的 secrets `kubectl edit secrets jenkins-maven-settings -n jx `
  * 在 data.settings.xml 中修改成 base64 加密后的内容

5. 在宿主机上使用 `jx open `查看能访问的域名，就可以进入jenkins ，默认账号密码 admin/admin


## 2. 初始化

1. 需要在全局配置  Global Pipeline Libraries 中添加共享仓库： jxlib

2. 建立 pipeline ，在 jenkins 中 新建任务 -> 流水线

3. 打开流水线的 配置，然后General -> 参数化构建过程 -> 添加参数

   * 添加文本参数 Name 为 tag ，Description  为 tag , 	默认值为 master

   





## 3.  springcloud


### 1.参数设置
 使用脚本实现发布，以 springcloud.Dev 为例，**APP_NAME为微服务名，要与需要启动的二级目录名一致**  ，branch = tag 的意思是传入 Git Parameter 插件的参数，也可以自定义，比如想拉取 master 分支，可以branch = "master"。

   * **注意：如果服务是一级目录，即目录下就是src目录和pom.xml，会直接在这个目录下mvn build ；如果存在二级目录，会进入二级目录，这个二级目录的名字要和APP_NAME一致，不然进不去。然后在这个目录下mvn build 。**




### 2.使用脚本发布

#### dev


```groovy

@Library('jxlib') 
import com.example.springcloud.Dev
pipeline {
    agent { label "jenkins-maven" }
    options { disableConcurrentBuilds() }
    
    environment {
    GIT_REPO          = "https://github.com/ica10888/jenkins-x-demo.git"
    APP_NAME          =  "demo"
    BRANCH_NAME       =  "${tag}"     
    PORT              =  8080
    JAVA_COMMAND      = "java -Xms256M -Xmx512M -jar app.jar"
    MAVEN_COMMAND     = "mvn -Dmaven.test.skip=true package"
    KUBERNETES_CPU    = "1000m"
    KUBERNETES_MENORY = "512Mi"
    }

    stages {
      stage('init item'){steps{script{Dev.initItem(this)}}}
      stage('library resource'){steps{script{Dev.libraryResource(this)}}}
      stage('maven build'){steps{script{Dev.mavenBuild(this)}}}
      stage('skaffol build'){steps{script{Dev.skaffolBuild(this)}}}
      stage('helm deploy'){steps{script{Dev.helmDeploy(this)}}}
      stage('predestroy'){steps{script{Dev.predestroy(this)}}}
      }
}
```

#### test


```groovy

@Library('jxlib') 
import com.example.springcloud.Test
pipeline {
    agent { label "jenkins-maven" }
    options { disableConcurrentBuilds() }
    
    environment {
    GIT_REPO          = "https://github.com/ica10888/jenkins-x-demo.git"
    APP_NAME          = "demo"
    BRANCH_NAME       = "${tag}"    
    PORT              =  8080
    JAVA_COMMAND      = "java -Xms256M -Xmx512M -Dspring.profiles.active=test -jar app.jar"
    MAVEN_COMMAND     = "mvn -Dmaven.test.skip=true package"
    KUBERNETES_CPU    = "1000m"
    KUBERNETES_MENORY = "512Mi"
    }

    stages {
      stage('init item'){steps{script{Test.initItem(this)}}}
      stage('library resource'){steps{script{Test.libraryResource(this)}}}
      stage('maven build'){steps{script{Test.mavenBuild(this)}}}
      stage('skaffol build'){steps{script{Test.skaffolBuild(this)}}}
      stage('helm deploy'){steps{script{Test.helmDeploy(this)}}}
    }
    post {success {script{Test.releaseTag(this)}}}

}
```



#### prod


```groovy

@Library('jxlib') 
import com.example.springcloud.Prod
pipeline {
    agent { label "jenkins-maven" }
    options { disableConcurrentBuilds() }
    
    environment {
    REPLICA_COUNT     = 2
    GIT_REPO          = "https://github.com/ica10888/jenkins-x-demo.git"
    APP_NAME          = "demo"
    BRANCH_NAME       = "${tag}"     
    PORT              =  8080
    JAVA_COMMAND      = "java -Xms1g -Xmx1g -Dspring.profiles.active=prod -jar app.jar"
    MAVEN_COMMAND     = "mvn -Dmaven.test.skip=true package"
    KUBERNETES_CPU    = "2000m"
    KUBERNETES_MENORY = "1024Mi"
    }

    stages {
      stage('init item'){steps{script{Prod.initItem(this)}}}
      stage('library resource'){steps{script{Prod.libraryResource(this)}}}
      stage('maven build'){steps{script{Prod.mavenBuild(this)}}}
      stage('skaffol build'){steps{script{Prod.skaffolBuild(this)}}}
      stage('helm deploy'){steps{script{Prod.helmDeploy(this)}}}
      stage('predestroy'){steps{script{Prod.predestroy(this)}}}
      }
}
```


## 4.  demo
(https://github.com/ica10888/jenkins-x-demo)[https://github.com/ica10888/jenkins-x-demo]
