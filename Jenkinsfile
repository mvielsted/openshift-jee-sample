#!groovy​
node {

	try {

		def mvnHome = tool 'M3'
		def dateTime = new Date().format("MM/dd/yyyy-HH:mm:ss:SSS")
		def jbossHome = env.JENKINS_JBOSS7_HOME
		def pom
		def version
		def deployZipFile ='in4mo-insurance-bin.zip'

		def developController = 'A04763:9990'
		def testController = 'A04392:9990'
		def qaController = 'A04762:9990'
		def productionController1 = 'A04761:9990'

		env.JAVA_HOME = tool 'Java 1.8'

		/**
		 * stage('Checkout')
		 * 1. Wipe the workspace
		 * 2. Check out a fresh version from bitbucket
		 * 3. Variables for later use is created.
		 *    Be aware that you need to pipe bat files to be able to read return values.
		 *    You have to delete files after use, otherwise you will recieve an error when you create a release branch.
		 *      After creation of the release branch it will be pushed to bitbucket, at that piont you will have uncomitted files in your workspace.
		 */
		stage('Checkout'){
			deleteDir()
			checkout scm

			pom = readMavenPom file: 'pom.xml'
			version = pom.version
		}

		/**
		 * 1. Standard mvn clean install executed
		 * 2. The artifact is archived in Jenkins
		 */
		stage('Build'){
    	bat "git rev-parse HEAD > commit"
    	def commit = readFile('commit').trim()
    	bat 'del commit'
    	bat "${mvnHome}/bin/mvn clean install -DskipTests -DGIT_COMMIT=${commit} -DBUILD_URL=${env.BUILD_URL} -DGIT_BRANCH=${env.BRANCH_NAME} -DBUILD_TIME=${dateTime}"
    	step([$class: 'ArtifactArchiver', artifacts: '**/target/*.war', fingerprint: true])
    }

    /**
     * Unit tests not run on release branches, hence unit tests are run in the develop branch just before.
     */
    stage('Execute unit test'){
    	if (!isRelease(env.BRANCH_NAME)){
    		bat "${mvnHome}/bin/mvn test"
    		step([$class: 'JUnitResultArchiver', testResults: '**/target/surefire-reports/TEST-*.xml'])
    	} else{
    		echo 'Skipping unit test on release build'
    	}
    }

		/**
		 * Code analysis only run on features
		 */
		stage('Execute sonarQube code analysis'){
			bat "${mvnHome}/bin/mvn sonar:sonar -Dsonar.host.url=http://bipbuild.dt2kmeta.dansketraelast.dk:9000"
		}

		/**
		 * Configure server and deploy
		 * Run integrationtest
		 * Start release button, always propose the next version
		 * If release is created it's pushed to bitbucket
		 */
		if (isDevelopment(env.BRANCH_NAME)) {

			stage ('Configure and deploy to DEV'){
			  	bat "${mvnHome}/bin/mvn flyway:migrate -Dflyway.configFile=config/flyway/flyway-dev.properties"
				configAndDeploy(jbossHome, developController, 'dev', 'admin', 'p@ssword1')
			}

			stage('Execute integration test on DEV'){
				bat "${mvnHome}/bin/mvn verify"
			}

			stage('Start release'){
				def newReleaseVersion = version.replace('-SNAPSHOT', '')
				def firstPart = newReleaseVersion.substring(0, newReleaseVersion.lastIndexOf('.')+1)
				def incrementSnapshot = newReleaseVersion.substring(newReleaseVersion.lastIndexOf(".")+1, newReleaseVersion.length()).toInteger()+1
				def newDevelopmentVersion = firstPart+incrementSnapshot.toString()+'-SNAPSHOT'

				timeout(time:30, unit:'MINUTES') {
					def userInput = input(
							message: 'Do you want start to start a release?', parameters: [
							string(defaultValue: newReleaseVersion,     description: '', name: 'ReleaseVersion'),
							string(defaultValue: newDevelopmentVersion, description: '', name: 'DevelopmentVersion')
					])
					def releaseVersion = (userInput['ReleaseVersion'])
					def developmentVersion = (userInput['DevelopmentVersion'])
					bat "${mvnHome}/bin/mvn jgitflow:release-start -DpushReleases=true -DreleaseVersion=${releaseVersion} -DdevelopmentVersion=${developmentVersion} -Dusername=stash_buildserver -Dpassword=stash_buildserver"
				}
			}
		}
		/**
		 * Releases are deployed to test
		 * Release finish prompt
		 * Mail sent to stakeholders
		 */

        if (isRelease(env.BRANCH_NAME)) {

			stage('Configure and deploy to TST'){
			  bat "${mvnHome}/bin/mvn flyway:migrate -Dflyway.configFile=config/flyway/flyway-test.properties"
			  configAndDeploy(jbossHome, testController, 'test', 'admin', 'p@ssword1')
			}

			stage('Configure and deploy to QA') {
				timeout(time: 5, unit: 'MINUTES') {
					def userInput = input(message: 'Send a deployment ready mail?')
					deployMail('Deployment for QA is ready')
				}
				timeout(time: 30, unit: 'MINUTES') {
					def userInput = input(message: 'Configure and deploy to QA?')
					bat "${mvnHome}/bin/mvn flyway:migrate -Dflyway.configFile=config/flyway/flyway-qa.properties"
					configAndDeploy(jbossHome, qaController, 'qa', 'admin', 'p@ssword1')
					successMail('Deployed to QA', version, env.INTEGRATION_TEAM)
				}
			}

			stage('Finish release'){
				timeout(time:30, unit:'MINUTES') {
					def userInput = input(message: 'Do you want start to finish this release?')
					bat "${mvnHome}/bin/mvn jgitflow:release-finish -DpushReleases=true -Dusername=stash_buildserver -Dpassword=stash_buildserver"
					successMail('Release finished', version, env.STAKEHOLDERS)
				}
			}
		}

        /**
		 * Master branches (new finished releases) are copied to the deployment folder
		 */

		if(isMaster(env.BRANCH_NAME)){
			stage('Archive in releasefolder'){
				def deploymentFolder = '\\\\a04397\\deployments\\bip-releases\\'
				def nexusArtifact = 'target\\'+deployZipFile
				def releaseFolder = deploymentFolder + pom.artifactId + '\\' + pom.version + '\\'
				def exists = fileExists releaseFolder + deployZipFile

				if (exists) {
					echo 'do exists'
					dir(releaseFolder) {
						echo 'deleting folder'
						deleteDir()
						echo 'deleted folder'
					}
					dir(env.WORKSPACE) {
						echo 'change back to workspace'
						bat 'xcopy /y ' + nexusArtifact + ' ' + releaseFolder
					}
				} else {
					echo 'we just copy'
					bat 'xcopy /y ' + nexusArtifact + ' ' + releaseFolder
				}
			}

			stage('Deploy to production'){

				timeout(time:30, unit:'MINUTES') {
                    def userInput = input(
                            message: 'Enter credentials for the production environment to deploy to production', parameters: [
                            string(defaultValue: '', description: '', name: 'Username'),
                            string(defaultValue: '', description: '', name: 'Password')
                    ])
                    def userName = (userInput['Username'])
                    def password = (userInput['Password'])
                    bat "${mvnHome}/bin/mvn flyway:migrate -Dflyway.configFile=config/flyway/flyway-prod.properties"
                    configAndDeploy(jbossHome, productionController1, 'prod', userName, password)
			        successMail('Deployed to production', version, env.INTEGRATION_TEAM)
                }
			}
		}
	} catch (org.jenkinsci.plugins.workflow.steps.FlowInterruptedException e){
		/**
		 * On all input boxes an abort action is caught and the build job gets status aborted and marked grey in build history
		 */
		echo "The job was cancelled or aborted"
		currentBuild.result = 'SUCCESS'
	} catch(e) {
		sendMailOnException(e)
		throw e
	} finally {
		/**
		 * Finally we always delete the workspace script folder,otherwise changes to the Jenkinsfile is not always detected.
		 */
		dir("${WORKSPACE}@script") {
			deleteDir()
		}
	}
}

@NonCPS
void sendMailOnException(Exception e) {
  def changeLog = getChangeLog(currentBuild)
  def mailSubject = 'Build failed in Jenkins: ' + env.JOB_NAME + ' #' + env.BUILD_NUMBER
  def mailBody = 'Project: ' + env.JOB_NAME + ' <br>Build #: ' + env.BUILD_NUMBER + ' <br> URL: ' + env.BUILD_URL + '<br>Changes:<br> ' + changeLog + '<br>Error cause:<br> '+e
  mail to: env.DEV_TEAM, subject: mailSubject, body: mailBody, charset: 'UTF-8', mimeType: 'text/html'
  echo 'Mail sent to dev team: ' + env.DEV_TEAM
}

@NonCPS
def getChangeLog(build) {
  if (build == null) {
    return 'No build'
  }
  def changeLog = build.changeSets.collect { cs ->
    /kind=${cs.kind}; entries=/ + cs.collect { entry ->
      /${entry.commitId} by ${entry.author.id} ~ ${entry.author.fullName} on ${new Date(entry.timestamp)}: ${entry.msg}: /+ entry.affectedFiles.collect { file ->
        /${file.editType.name} ${file.path}/
      }.join('; ')
    }.join(', ')
  }.join(' & ')
  echo 'changeLog=' + changeLog
  return changeLog
}

void successMail(String messageType, String version, String recipients) {
	def mailSubject = messageType + ' : ' + env.JOB_NAME + ' #' + env.BUILD_NUMBER
	def mailBody = 'Project: ' + env.JOB_NAME + ' <br>Version: ' + version + ' <br>Build #: ' + env.BUILD_NUMBER + ' <br> URL: ' + env.BUILD_URL
	mail to: recipients, subject: mailSubject, body: mailBody, charset: 'UTF-8', mimeType: 'text/html'
	echo 'Success mail sent to: ' + recipients
}

void deployMail(String messageType) {
	def mailSubject = messageType + ' : ' + env.JOB_NAME + ' #' + env.BUILD_NUMBER
	def mailBody = 'The following release is ready to be deployed to the QA environment: ' + ' <br> Deployment URL: ' + env.BUILD_URL
	mail to: env.STAKEHOLDERS, subject: mailSubject, body: mailBody, charset: 'UTF-8', mimeType: 'text/html'
	echo 'Success mail sent to: ' + env.STAKEHOLDERS
}

void configAndDeploy(String p_jbossHome,String p_controller, String p_environment,String p_username,String p_password){
	bat "config/config.bat " + p_jbossHome + " " + p_environment + " " + p_controller + " " + p_username + " " + p_password
	sleep(40)
	bat "config/DSConnectionTest.bat " + p_jbossHome + " " + p_environment + " " + p_controller + " " + p_username + " " + p_password
	bat p_jbossHome+"/bin/jboss-cli.bat --controller="+ p_controller +" --connect --user="+p_username+" --password="+p_password+" --command=\"deploy target/in4mo-insurance.war --force\""
}

@NonCPS
private static boolean isMaster(branchName) {
	return branchName.contains('master')
}

@NonCPS
private static boolean isFeature(branchName) {
	return branchName.contains('feature/')
}

@NonCPS
private static boolean isRelease(branchName) {
	return branchName.contains('release/')
}

@NonCPS
private static boolean isDevelopment(branchName) {
	return branchName.contains('develop')
}