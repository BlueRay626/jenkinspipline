import java.text.SimpleDateFormat
import groovy.sql.Sql
//git url
def ecgiturl ='http://ray.aws.com/asus-ec/aws-code.git'
def sysgiturl ='http://ray.aws.com/ray/system_config.git'
def newmangiturl ='http://ray.aws.com/asus-ec/postman-api.git'
def ecgwgiturl ='http://ray.aws.com/ECGW/ECGW.git'

//git branch
def ecbranch = 'develop'
def ecgwbranch = 'develop'
def sysbranch ='master'
def newmanbranch = 'master'

//server path
def tomcathome= '/opt/apache-tomcat-8.0.35'
def ecgwserverpath = '/opt/apache-tomcat-8.0.35/bak'
def serverpath ='/SharedNFS'
def sysgitpath = '/api/config_comm/db/dev'
def preserverpath ='data/workspace/predev/aws-code'
def jenkinspath = '/var/jenkins_home/jobs/awsbuild'
def sysconftag = 'dev'

//server ip
def serverip ='webdata@127.0.0.1'
def ecgwserverip ='rayaws@127.0.0.1'
def proxyip = '127.0.0.1:3128'
def lineuserid = ["1111111","1111111", "1111111", "1111111", "1111111","1111111"]
def testurlloop = ["https://ray.aws.com/XW000013/"]


//mysql env
def dsqlgroup = "DEV"
def dateFormat = new SimpleDateFormat("yyyyMMddHHmm")
def jdate = dateFormat.format(new Date())

//jenkins key
def credId =  '1111111'
def newmancredId = '1111111'

def phpmd5check = ''
def ecgwmd5check = ''

def bodymsg = ''
def pipurl = ''
def msgflag = ''
def msgmemo = ''

def phplast = ''
def ecgwlast = ''

def notifyStarted(String msgflag,String pipurl) {
	if (msgflag == 'FAILURE') {
		bodymsg = '<FONT COLOR="#FF3366"><B>'+msgflag+'</B></FONT>'
	} else {
		bodymsg = '<FONT COLOR="GREEN"><B>'+msgflag+'</B></FONT>'
	}
	emailext attachmentsPattern: 'Reports/*.html', body: 'Deploy is '+bodymsg+'  check console output at '+pipurl+' to view the results.',
			recipientProviders: [
					[$class: 'CulpritsRecipientProvider'],
					[$class: 'DevelopersRecipientProvider'],
					[$class: 'RequesterRecipientProvider']
			],
			replyTo: '$DEFAULT_REPLYTO',
			subject: '$DEFAULT_SUBJECT'+msgflag,
			to: '$DEFAULT_RECIPIENTS'
}

def getlastsql(dsqlgroup,tag){
	def conn = Sql.newInstance('jdbc:mysql://ray.aws.com/jenkins','ray', '1111111', 'com.mysql.jdbc.Driver')
	def queryphpsql = "SELECT J_NAME FROM D_HISTORY WHERE J_GROUP =${dsqlgroup} and J_ENV=${tag} and J_FLAG='SUCCESS' ORDER BY J_NO DESC LIMIT 1;"
    def last = conn.firstRow(queryphpsql).J_NAME.toString()
	return last
}

def insertphpsql (dsqlgroup,jdate,msgflag,msgmemo){
	def conn = Sql.newInstance('jdbc:mysql://ray.aws.com/jenkins','ray', '1111111', 'com.mysql.jdbc.Driver')
	conn.execute('INSERT INTO D_HISTORY  (J_GROUP, J_ENV, J_NAME,J_FLAG,J_MEMO, CREATE_DATE, MODIFY_DATE) VALUES  ("'+dsqlgroup+'","PHP","aws-php.tar.gz-'+jdate+'.tar.gz","'+msgflag+'","'+msgmemo+'",current_timestamp,current_timestamp)')
}

def insertecgwsql (dsqlgroup,jdate,msgflag,msgmemo){
	def conn = Sql.newInstance('jdbc:mysql://ray.aws.com/jenkins','ray', '1111111', 'com.mysql.jdbc.Driver')
	conn.execute('INSERT INTO D_HISTORY  (J_GROUP, J_ENV, J_NAME,J_FLAG,J_MEMO, CREATE_DATE, MODIFY_DATE) VALUES  ("'+dsqlgroup+'","ECGW","ECGW-'+jdate+'.war","'+msgflag+'","'+msgmemo+'",current_timestamp,current_timestamp)')
}

def deployfunction(String serverip,String ecgwserverip, String serverpath,String tomcathome){
	parallel (
		php: {
			echo 'DEPLOY PHP'
			sh 'ssh -i /rayaws/rayaws-key '+serverip+' "cd '+serverpath+' ; ./ecDeploy.sh ;"'
		}
		/*,
		ecgw: {
			echo 'DEPLOY ECGW'
			sh 'ssh -i /rayaws/rayaws-key '+ecgwserverip+' "'+tomcathome+'/jenkins_script/deploy_by_ecgw-api_new.sh \\& "'
		}
		*/
	)
}
//ray_chang@asus.com
//$DEFAULT_RECIPIENTS
node {
	lineurl = 'https://isbaas.azurewebsites.net/api/LineWebHook?key=1111111&id='
	msgflag ='BEGIN'
	matterwhook = 'http://ray.aws.com/mattermost/hooks/rayaws79cyo7tmwox8mga'
	mattercolor = 'good'
	mattericon = 'jenkins'
	matterchannel = 'GitLab'
	pipurl = 'http://ray.aws.com/jenkins/blue/organizations/jenkins/'+"${env.JOB_NAME}"+'/detail/'+"${env.JOB_NAME}/${env.BUILD_NUMBER}"+'/pipeline/'
	try {
		stage("Check Jenkins Hosts") {
				sh "cd /var/jenkins_home/workspace/; ./checkhosts.sh"
		}
		stage("WEB HOOK LINE/MATTERMOST MESSAGE") {
		  	echo 'line Bot & Mattermost Hook'
				/*
				withEnv(['HTTPS_PROXY=http://'+proxyip+'']) {
				  for (id in lineuserid) {
						sh "curl '"+lineurl+id+'&msg='+env.JOB_NAME+'-DEPLOY-'+msgflag+"'"
				  }
				}
				*/
			  mattermostSend channel: matterchannel, color: mattercolor, endpoint: matterwhook, icon: mattericon, message: "${env.JOB_NAME} # ${env.BUILD_NUMBER} :  ${msgflag} (<${pipurl}|Open>)"
		}

		stage("DNS CHECK") {
			sh "ping -c 4 -q 127.0.0.1 | grep -oP '\\d+(?=% packet loss)' > commandResult"
		  env.status = readFile('commandResult').trim()
			if(env.status != '0'){
				throw new Exception("packet loss != 0 "+env.status);
			}
		}

		stage("CHECKOUT PHP & ECGW") {
			echo 'Remove history report'
			sh 'rm -Rf ./aws-php*.tar.gz ./sysconf.tar.gz ./Reports ./project ./conf ./ecgw'
			echo 'check out'
			dir('project'){
				retry(2){
					checkout([$class: 'GitSCM',
                         branches: [[name: '*/' + ecbranch + '']],
                         doGenerateSubmoduleConfigurations: false,
                         extensions: [[$class: 'CleanBeforeCheckout'],[$class: 'CloneOption', depth: 1, noTags: false]],
                         submoduleCfg: [],
                         userRemoteConfigs: [[credentialsId: credId, url: ecgiturl]]
                    ])
				}
				sh "chown -Rf rayaws:rayaws  ../project"
			}
			//tns sysconfig source code
			dir('conf') {
				retry(2) {
					checkout([$class           : 'GitSCM',
							  branches         : [[name: '*/' + sysbranch + '']],
							  extensions       : [[$class: 'SparseCheckoutPaths', sparseCheckoutPaths: [[path: sysgitpath]]]],
							  userRemoteConfigs: [[credentialsId: credId,
												   url          : sysgiturl]]])
				}
				sh "chown -Rf rayaws:rayaws  ../conf"
			}
			echo 'CHECKOUT ECGW'
			dir('ecgw'){
				retry(2){
					checkout([$class: 'GitSCM',
                         branches: [[name: '*/' + ecgwbranch + '']],
                         doGenerateSubmoduleConfigurations: false,
                         extensions: [[$class: 'CleanBeforeCheckout'],[$class: 'CloneOption', depth: 1, noTags: false]],
                         submoduleCfg: [],
                         userRemoteConfigs: [[credentialsId: credId, url: ecgwgiturl]]
          ])
				}
				sh "chown -Rf rayaws:rayaws  ../ecgw"
			}
		}

		stage("BUILD PHP & ECGW") {
			echo 'BUILD PHP'
			dir('project'){
				echo 'Build project'
				sh "tar -zcf ../aws-php.tar.gz  --exclude='.git' --exclude='./php_cron*' --exclude='./php_tnsrayaws*' --exclude='./aws-php.tar.gz*'  ./"
				sh "cp ../aws-php.tar.gz ../aws-php.tar.gz-${jdate}.tar.gz"
				sh "chown -Rf rayaws:rayaws ../aws-php.tar.gz ../aws-php.tar.gz-${jdate}.tar.gz"

			}
			dir('conf/'+sysgitpath){
				echo 'Build conf'
				sh "tar -zcf ../../../../../sysconf.tar.gz --exclude='.git'  ./"
				sh "chown -Rf rayaws:rayaws  ../../../../../sysconf.tar.gz"
			}
			echo 'BUILD ECGW'
			dir('ecgw'){
				sh 'chmod 755 gradlew gradlew.bat build.gradle'
				sh './gradlew clean tasks :ecgw-api:war'
			}
			dir('ecgw/ecgw-api'){
				sh "mv ./build/libs/AWSECGW-2.0.0.war ./build/libs/ECGW.war"
				sh "cp ./build/libs/ECGW.war ./build/libs/ECGW-${jdate}.war"
				sh "chown -Rf rayaws:rayaws  ./build/libs/ECGW.war ./build/libs/ECGW-${jdate}.war"
			}
		}

		stage("UPLOAD PHP & ECGW") {
			echo 'UPLOAD PHP'
			sh "scp -i /rayaws/rayaws-key aws-php.tar.gz ${serverip}:${serverpath}/"
			sh "scp -i /rayaws/rayaws-key aws-php.tar.gz-${jdate}.tar.gz ${serverip}:${serverpath}/php_bak/"
			sh "scp -i /rayaws/rayaws-key sysconf.tar.gz ${serverip}:${serverpath}/php_api_common/_new_acom/config_comm/db/${sysconftag}"

			echo 'UPLOAD ECGW'
			dir('ecgw/ecgw-api//build/libs'){
				sh "scp -i /rayaws/rayaws-key ECGW.war ${ecgwserverip}:${ecgwserverpath}/"
				sh "scp -i /rayaws/rayaws-key ECGW-${jdate}.war ${ecgwserverip}:${ecgwserverpath}/"
			}

		}

		stage("CHECK SERVER SOURCE CODE") {
			sh "md5sum  aws-php.tar.gz > commandResult"
			phpmd5check = readFile('commandResult').trim()
			sh 'ssh -i /rayaws/rayaws-key '+serverip+' "md5sum '+serverpath+'/aws-php.tar.gz > commandResult "'
			env.status = readFile('commandResult').trim()
			if(phpmd5check != env.status){
				throw new Exception("CHECK SERVER  PHP SOURCE CODE  is diff "+phpmd5check);
			}
			dir('ecgw/ecgw-api//build/libs'){
				sh "md5sum  ECGW.war > commandResult"
				ecgwmd5check = readFile('commandResult').trim()
				sh 'ssh -i /rayaws/rayaws-key '+ecgwserverip+' "md5sum '+ecgwserverpath+'/ECGW.war > commandResult "'
				env.status = readFile('commandResult').trim()
				if(ecgwmd5check != env.status){
					throw new Exception("CHECK SERVER  ECGW SOURCE CODE  is diff "+phpmd5check);
				}
			}
		}

		stage("DEPLOY PHP & ECGW") {
			deployfunction( serverip, ecgwserverip,  serverpath, tomcathome)
		}

		stage("Message") {
			echo 'Message Deploy '+msgflag+' End'
			mattercolor = 'good'
			msgflag ='SUCCESS'
		}

	} catch (Exception e) {
		echo "Exception "+ e.toString().trim()
		msgmemo = "Exception "+ e.toString().trim()
		mattercolor = 'danger'
		msgflag = 'FAILURE'
		sh "exit 1"
	} finally {
		/*
		withEnv(['HTTPS_PROXY=http://'+proxyip+'']) {
			for (id in lineuserid) {
				sh "curl '"+lineurl+id+'&msg='+env.JOB_NAME+'-DEPLOY-'+msgflag+"'"
			}
		}
		*/
		mattermostSend channel: matterchannel, color: mattercolor, endpoint: matterwhook, icon: mattericon, message: "${env.JOB_NAME} # ${env.BUILD_NUMBER} :  ${msgflag} (<${pipurl}|Open>)"
		if('FAILURE' == msgflag){
			notifyStarted(msgflag,pipurl)
			sh "exit 1"
		}
	}
}