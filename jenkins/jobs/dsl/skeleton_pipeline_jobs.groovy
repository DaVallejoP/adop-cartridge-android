// Folders
def workspaceFolderName = "${WORKSPACE_NAME}"
def projectFolderName = "${PROJECT_NAME}"

// Variables
// **The git repo variables will be changed to the users' git repositories manually in the Jenkins jobs**

def skeletonAppGitUrl = "https://github.com/googlesamples/android-architecture.git"
def regressionTestGitUrl = "https://github.com/googlesamples/android-architecture.git"

// ** The logrotator variables should be changed to meet your build archive requirements
def logRotatorDaysToKeep = 7
def logRotatorBuildNumToKeep = 7
def logRotatorArtifactsNumDaysToKeep = 7
def logRotatorArtifactsNumToKeep = 7

// Jobs
def buildAppJob = freeStyleJob(projectFolderName + "/Android_Application_Build")
def unitTestJob = freeStyleJob(projectFolderName + "/Android_Application_Unit_Tests")
def codeAnalysisJob = freeStyleJob(projectFolderName + "/Android_Application_Code_Analysis")
def deployJob = freeStyleJob(projectFolderName + "/Android_Application_Deploy")
def regressionTestJob = freeStyleJob(projectFolderName + "/Android_Application_Regression_Tests")

// Views
def pipelineView = buildPipelineView(projectFolderName + "/Android_Application")

pipelineView.with{
    title('Android Application Pipeline')
    displayedBuilds(5)
    selectedJob(projectFolderName + "/Android_Application_Build")
    showPipelineParameters()
    showPipelineDefinitionHeader()
    refreshFrequency(5)
}

// All jobs are tied to build on the Jenkins slave
// The functional build steps for each job have been left empty
// A default set of wrappers have been used for each job
// New jobs can be introduced into the pipeline as required

buildAppJob.with{
  description("Android application build job.")
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  scm{
    git{
      remote{
        url(skeletonAppGitUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/todo-mvp")
    }
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("android")
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  triggers {
    gerrit {
      events {
          refUpdated()
      }
      project(projectFolderName + '/' + skeletonAppGitUrl, 'plain:master')
      configure { node ->
          node / serverName("ADOP Gerrit")
      }
    }
  }
  steps {
    shell('''cd todoapp
      ./gradlew app:assembleMockDebug'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      archiveArtifacts("**/*")
      trigger(projectFolderName + "/Android_Application_Unit_Tests"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${BUILD_NUMBER}')
          predefinedProp("PARENT_BUILD", '${JOB_NAME}')
        }
      }
    }
  }
}

unitTestJob.with{
  description("This job runs unit tests on our skeleton application.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Android_Application_Build","Parent build name")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("android")
  steps {
    copyArtifacts("Android_Application_Build") {
        buildSelector {
            buildNumber('${B}')
        }
    }
  }
  steps {
    shell('''cd todoapp
      ./gradlew app:testMockDebugUnitTest'''.stripMargin())
  }
  publishers{
    archiveArtifacts("**/*")
    downstreamParameterized{
      trigger(projectFolderName + "/Android_Application_Code_Analysis"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD",'${PARENT_BUILD}')
        }
      }
    }
  }
}

codeAnalysisJob.with{
  description("This job runs code quality analysis for our skeleton application using SonarQube.")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Android_Application_Build","Parent build name")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  label("android")
  steps {
      copyArtifacts('Android_Application_Unit_Tests') {
          buildSelector {
              buildNumber('${B}')
          }
      }
  }
  steps {
    shell('''cd todoapp
      ./gradlew app:lintMockDebug'''.stripMargin())
  }
  publishers{
    androidLint('**/lint-results.xml') {
      healthLimits(3, 20)
      thresholdLimit('high')
      defaultEncoding('UTF-8')
      canRunOnFailed(true)
      useStableBuildAsReference(true)
      useDeltaValues(true)
      computeNew(true)
      shouldDetectModules(true)
      thresholds(
        unstableTotal: [all: 1, high: 2, normal: 3, low: 4],
        failedTotal: [all: 5, high: 6, normal: 7, low: 8],
        unstableNew: [all: 9, high: 10, normal: 11, low: 12],
        failedNew: [all: 13, high: 14, normal: 15, low: 16]
      )
    }
    downstreamParameterized{
      trigger(projectFolderName + "/Android_Application_Deploy"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
        }
      }
    }
  }
}

deployJob.with{
  description("This job deploys the skeleton application to the CI environment")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Android_Application_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("android")
  steps {
    shell('''## YOUR DEPLOY STEPS GO HERE'''.stripMargin())
  }
  publishers{
    downstreamParameterized{
      trigger(projectFolderName + "/Android_Application_Regression_Tests"){
        condition("UNSTABLE_OR_BETTER")
        parameters{
          predefinedProp("B",'${B}')
          predefinedProp("PARENT_BUILD", '${PARENT_BUILD}')
          predefinedProp("ENVIRONMENT_NAME", '${ENVIRONMENT_NAME}')
        }
      }
    }
  }
}

regressionTestJob.with{
  description("This job runs regression tests on the deployed skeleton application")
  parameters{
    stringParam("B",'',"Parent build number")
    stringParam("PARENT_BUILD","Android_Application_Build","Parent build name")
    stringParam("ENVIRONMENT_NAME","CI","Name of the environment.")
  }
  logRotator {
    daysToKeep(logRotatorDaysToKeep)
    numToKeep(logRotatorBuildNumToKeep)
    artifactDaysToKeep(logRotatorArtifactsNumDaysToKeep)
    artifactNumToKeep(logRotatorArtifactsNumToKeep)
  }
  scm{
    git{
      remote{
        url(regressionTestGitUrl)
        credentials("adop-jenkins-master")
      }
      branch("*/todo-mvp")
    }
  }
  wrappers {
    preBuildCleanup()
    injectPasswords()
    maskPasswords()
    sshAgent("adop-jenkins-master")
  }
  environmentVariables {
      env('WORKSPACE_NAME',workspaceFolderName)
      env('PROJECT_NAME',projectFolderName)
  }
  label("android")

  steps {
    shell('''cd todoapp
      adb connect android-emulator:5555
adb shell input keyevent 82 && adb shell input keyevent 66
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 0
./gradlew app:connectedMockDebugAndroidTest sonarqube
'''.stripMargin())
  }
}
