class Test{
    
    static def GIT_REPO          = ""
    static def APP_NAME          = ""
    static def BRANCH_NAME       = ""
    static def PORT              = ""
    static def JAVA_COMMAND      = ""
    static def MAVEN_COMMAND     = ""
    static def KUBERNETES_CPU    = ""
    static def KUBERNETES_MENORY = ""
    static def CHARTMUSEUM_CREDS = ""
    static def IMAGE_TAG         = ""
    static def PROFILE           = "test"
    static def ORG               = 'ica10888'
    static def DEPLOY_NAMESPACE  = "jx-staging"

    static def initItem(caller) {

        GIT_REPO          = caller.GIT_REPO
        APP_NAME          = caller.APP_NAME
        BRANCH_NAME       = caller.BRANCH_NAME
        PORT              = caller.PORT
        JAVA_COMMAND      = caller.JAVA_COMMAND
        MAVEN_COMMAND     = caller.MAVEN_COMMAND
        KUBERNETES_CPU    = caller.KUBERNETES_CPU
        KUBERNETES_MENORY = caller.KUBERNETES_MENORY

        CHARTMUSEUM_CREDS = caller.credentials('jenkins-x-chartmuseum')
        IMAGE_TAG         = "2.${caller.BUILD_NUMBER}." + (Math.random() * 900 +100).intValue().toString() + "-t-SNAPSHOT"

        //git 操作
        caller.sh "git clone ${GIT_REPO} ./"
        caller.sh "git checkout ${BRANCH_NAME}"
        caller.container('maven') {
            caller.sh "git config --global credential.helper store"
            caller.sh "jx step git credentials"             
        }
        //move fold to subfold
        if( caller.fileExists('src') ){
            caller.sh "mkdir -p /tmp/${APP_NAME}"
            caller.sh "mv   ${caller.WORKSPACE}/*   /tmp/${APP_NAME}"
            caller.sh "mv   ${caller.WORKSPACE}/.[^.]*   /tmp/${APP_NAME}"
            caller.sh "mv   /tmp/${APP_NAME}  ${caller.WORKSPACE}"
        }       

    }

    

    static def libraryResource(caller){
        // library resource
        caller.dir("${APP_NAME}"){
            caller.writeFile file: ".dockerignore" , text: caller.libraryResource("com/example/springcloud/.dockerignore")
            caller.writeFile file: "skaffold.yaml" , text: caller.libraryResource("com/example/springcloud/skaffold.yaml")
            caller.writeFile file: "watch.sh" , text: caller.libraryResource("com/example/springcloud/watch.sh")
        }
        // library resource chart
        caller.dir("${APP_NAME}") {
            if( ! caller.fileExists('chart') ){
                caller.sh "mkdir -p charts/${APP_NAME}/templates"
                caller.dir("charts/${APP_NAME}") {
                    caller.writeFile file: ".helmignore", text: caller.libraryResource("com/example/springcloud/charts/demo/.helmignore")
                    caller.writeFile file: "Chart.yaml", text: caller.libraryResource("com/example/springcloud/charts/demo/Chart.yaml")
                    caller.writeFile file: "Makefile", text: caller.libraryResource("com/example/springcloud/charts/demo/Makefile")
                    caller.writeFile file: "values.yaml", text: caller.libraryResource("com/example/springcloud/charts/demo/values.yaml")
                    caller.sh "sed -i -e 's/demo/${APP_NAME}/' Chart.yaml"
                    caller.sh "sed -i -e 's/demo/${APP_NAME}/' values.yaml"
                    caller.sh "sed -i -e 's/javacommand/${JAVA_COMMAND}/' values.yaml"
                    //修改 cpu 和 内存参数
                    def REQUESTS_CPU = Integer.parseInt(KUBERNETES_CPU.replaceAll("[^0-9]", ""))*4/5 as int  
                    REQUESTS_CPU = REQUESTS_CPU + "m"
                    def REQUESTS_MENORY = Integer.parseInt(KUBERNETES_MENORY.replaceAll("[^0-9]", ""))*4/5 as int  
                    REQUESTS_MENORY = REQUESTS_MENORY + "Mi"                     
                    caller.sh "sed -i -e 's/500m/${KUBERNETES_CPU}/' values.yaml"
                    caller.sh "sed -i -e 's/1000Mi/${KUBERNETES_MENORY}/' values.yaml"
                    caller.sh "sed -i -e 's/400m/${REQUESTS_CPU}/' values.yaml"
                    caller.sh "sed -i -e 's/800Mi/${REQUESTS_MENORY}/' values.yaml"
                    caller.sh "sed -i -e 's/8080/${PORT}/' values.yaml"


                }
                caller.dir("charts/${APP_NAME}/templates") {
                    caller.writeFile file: "_helpers.tpl", text: caller.libraryResource("com/example/springcloud/charts/demo/templates/_helpers.tpl")
                    caller.writeFile file: "deployment.yaml", text: caller.libraryResource("com/example/springcloud/charts/demo/templates/deployment.yaml")
                    caller.writeFile file: "NOTES.txt", text: caller.libraryResource("com/example/springcloud/charts/demo/templates/NOTES.txt")
                    caller.writeFile file: "service.yaml", text: caller.libraryResource("com/example/springcloud/charts/demo/templates/service.yaml")
                }
            }
        }
        // library resource Dockerfile
        caller.dir("${APP_NAME}"){
            if( ! caller.fileExists('Dockerfile') ){
                caller.writeFile file: "Dockerfile" , text: caller.libraryResource("com/example/springcloud/Dockerfile")
                caller.sh "sed -i -e 's/8080/${PORT}/' Dockerfile"
            }
        }

    }



    static def mavenBuild(caller){

        caller.dir("${APP_NAME}"){
            caller.container('maven') {
                //做版本号
                caller.sh "echo ${IMAGE_TAG} > VERSION"
                caller.sh "mvn versions:set -DnewVersion=\$(cat VERSION)"
                caller.dir("charts/${APP_NAME}"){
                    caller.sh "sed -i -e 's/version:.*/version: ${IMAGE_TAG}/' Chart.yaml"
                    caller.sh "sed -i -e 's|repository: .*|repository: 10.68.22.54:5000/${ORG}/${APP_NAME}|' values.yaml"
                    caller.sh "sed -i -e 's/tag: .*/tag: ${IMAGE_TAG}/' values.yaml"
                }	
                //maven 打包
                caller.sh "${MAVEN_COMMAND}"
            }
        }
    }



    static def skaffolBuild(caller){
        caller.dir("${APP_NAME}"){
            caller.container('maven') {
                //做镜像
                caller.sh "sed -i -e 's|/repo/image|/${ORG}/${APP_NAME}|' skaffold.yaml"
                caller.sh "sed -i -e 's/demo/${APP_NAME}/' skaffold.yaml"
                caller.sh 'export VERSION=`cat VERSION` && skaffold build -f skaffold.yaml'
                caller.sh "jx step post build --image ${caller.DOCKER_REGISTRY}/${ORG}/${APP_NAME}:\$(cat VERSION)"
            }
        }

    }



    static def helmDeploy(caller){
        caller.dir("${APP_NAME}/charts/${APP_NAME}"){
            caller.container('maven') {
                caller.sh 'helm init --client-only --upgrade -i registry.cn-hangzhou.aliyuncs.com/google_containers/tiller:v2.10.0-rc.1 --stable-repo-url https://kubernetes.oss-cn-hangzhou.aliyuncs.com/charts'
                caller.sh 'jx step helm release'
                caller.sh 'jx step helm build'
                caller.sh "jx step helm apply --name ${APP_NAME}  --namespace ${DEPLOY_NAMESPACE}"
                }
            }
        
    }
    
  
    static def releaseTag(caller){
        caller.input """[Proceed : hit tag] [Abort : do nothing]
        Select Proceed or Abort to terminate the build pod"""
        //这里写打tag的函数
        caller.echo """ 
        ==================================================================================================================
                                                                     ......                                    
        ....@@@@@@@@@@@@@@@@@@@*.      ....,@@@@@@@@@@@@@ ................,]@@@@@@@@ ]..       ...=@@@@@@@@@@@@@@@@@@^.
        ....@@@@@@@@@@@@@@@@@@@`.      ....=@@@@@@@@@@@@@@............../@@@@@@@@@@@@@*.        ..=@@@@@@@@@@@@@@@@@@^.
        ....[[[[[[ @@@@@/[[[[[[..      ....=@@@@@[[[[[[[[[............*@@@@@@/[..*[[@@*..       ..,[[[[[[@@@@@@[[[[[[`.
        ..........*@@@@@^.......       ....=@@@@@*....................=@@@@@`...........        ......... @@@@@........
        .. .  . ..=@@@@@^......        ....=@@@@@*....................=@@@@@@]...........        ..     .@@@@@@...... .
                ..=@@@@@^......        ....=@@@@@@@@@@@@@*.....  ...... @@@@@@@@ ].......               .@@@@@@.       
                ..=@@@@@^......        ....=@@@@@@@@@@@@@*...............[@@@@@@@@@@ ....               .@@@@@@.       
                ..=@@@@@^......        ....=@@@@@[[[[[[[[..................., @@@@@@@@`..               .@@@@@@.       
                ..=@@@@@^......        ....=@@@@@...............................,@@@@@@..               .@@@@@@.       
                ..=@@@@@^......        ....=@@@@@.............. ......,`.... ....=@@@@@*.               .@@@@@@.       
                ..=@@@@@^......        ....=@@@@@@@@@@@@@@`... .......=@@@ ]]]]]@@@@@@/.                .@@@@@@.       
                ..=@@@@@^......        ....=@@@@@@@@@@@@@@^...........=@@@@@@@@@@@@@@`...               .@@@@@@.       
                ..*[[[[[`... ...        ...,[[[[[[[[[[[[[[`.............[[@@@@@@@[`.....               ..,[[[[[..      
                ......... ..   .        ........        ........       .............                    ........          　　

        git仓库                    ${GIT_REPO}
        服务名                     ${APP_NAME}          
        分支                       ${BRANCH_NAME}
        镜像tag                    ${IMAGE_TAG}
        ==================================================================================================================
        """
        caller.cleanWs()
    }

}