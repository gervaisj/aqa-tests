#!groovy

List<Map<String, Object>> json = []

node(params.LABEL) {
  checkout scm
//  stage('Triggering python script')
//  {
//    sh """ls -1dq ${WORKSPACE}/aqa-tests/openjdk/excludes/* |
//    python3 ${WORKSPACE}/aqa-tests/scripts/disabled_tests/exclude_parser.py > ${WORKSPACE}/aqa-tests/scripts/disabled_tests/problem_list.json"""
//    trigger_issue_status()
//  }
  stage('readJSON') {
    def json_path = "${WORKSPACE}/aqa-tests/buildenv/jenkins/sample_output.json"
    json = readJSON(file: json_path) as List<Map<String, String>>
  }
}

stage('Launch Grinder Jobs')
        {
          launch_grinders(json)
        }

interface Exclusion extends Map<String, String> {}

/**
 Get the parameters specified as a string list.
 Get each of the key-value pairs for each json value.
 If the issue for a job is closed, and JDK_VERSION and
 JDK_IMPL match the parameters specified,
 run grinder on it. Otherwise ignore it
 */
def launch_grinders(List<Exclusion> json) {

  def jdk_ver = params.JDK_VERSION.split(',')
  def jdk_imp = params.JDK_IMPL.split(',')

  def closed_issues = json.findAll {
    it["ISSUE_TRACKER_STATUS"] == "closed"
  }


  Map<String, Exclusion> entries = closed_issues.groupBy {
    it.toMapString()
  }.collectEntries { exclKey, issues ->  // all list have len = 1, so keep only the first
    [exclKey, issues[0]]
  }

  Map<String, Closure> test_jobs = entries.collectEntries { exclKey, issue ->
    [exclKey, run_grinder(issue)]
  }

  parallel test_jobs
}

/**
 Consumes git token and triggers issue_tracker
 Generates output.json
 */
def trigger_issue_status() {

  if (params.AQA_ISSUE_TRACKER_CREDENTIAL_ID) {
    withCredentials([string(credentialsId: "${params.AQA_ISSUE_TRACKER_CREDENTIAL_ID}", variable: 'TOKEN')]) {
      sh """export AQA_ISSUE_TRACKER_GITHUB_USER=eclipse_aqavit
        export AQA_ISSUE_TRACKER_GITHUB_TOKEN=${TOKEN}
        python3 ${WORKSPACE}/aqa-tests/scripts/disabled_tests/issue_status.py --infile ${WORKSPACE}/aqa-tests/scripts/disabled_tests/problem_list.json > ${WORKSPACE}/aqa-tests/scripts/disabled_tests/output.json"""
    }
  }
}

/**
 This runs when we have found a job w/ git_issue_status = closed.
 Collects all parameters and runs grinder
 */
def run_grinder(Exclusion map) {
  def childParams = map.collect { k, v -> string(name: k, value: v) }
  return {
    build job: "Grinder", parameters: childParams, propagate: true
  }
}