# Authors: Guannan Zhao, Yingyi Luo
# Email: guannanzhao2023@u.northwestern.edu
# Email: yingyiluo2015@u.northwestern.edu

import requests, json, os, subprocess, shutil, time
import traceback
from difflib import SequenceMatcher

# murphy.wot.eecs.northwestern.edu config:
JAVA_HOME = '/usr/lib/jvm/jre-1.8.0-openjdk.x86_64/'
MVN = '~/apache-maven-3.6.2/bin/mvn'

# typical mac configuration:
#JAVA_HOME = '/Applications/IntelliJ IDEA CE.app/Contents/jbr/Contents/Home'
#MVN = '/usr/local/bin/mvn'

# typical Windows configuration:
#JAVA_HOME = "C:\\Program Files\\Java\\jdk-13"
#MVN = "C:\\Program Files\\apache-maven-3.6.2\\bin\\mvn.cmd"

# absolute path to your search-api-1.0-SNAPSHOT.war file
# e.g., WAR_ABS_PATH = '/home/<netid>/search-api-test/search-api-1.0-SNAPSHOT.war'
WAR_ABS_PATH = '/home/yly0746/comp396hw2/search-api-1.0-SNAPSHOT.war'

ENV_VARS = {
  "JAVA_HOME": JAVA_HOME,
  "AWS_ACCESS_KEY_ID": "AKIA3ATZL6C6SW7TWSWO",
  "AWS_SECRET_ACCESS_KEY": "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",
  "ELASTIC_SEARCH_HOST": "search-news-prod-hn5fzerrq2xj62y5b4l466nfk4.us-east-2.es.amazonaws.com",
  "ELASTIC_SEARCH_INDEX": "articles",
}

LOCAL_HOST = "http://localhost:8080"
REFERENCE_HOST = "http://ssa-hw2-backend.stevetarzia.com"
API_PATH = "/api/search"

# Points: for hw2, there are 50 in total:
POINT_COMPILE_SUCCESS = 10
POINT_EXEC_SUCCESS = 10
POINT_CORRECT_STATUS_CODE = 2 # 2*4 == 8pts
POINT_EXACT_SEARCH_RESULT = 1 # 1*22 == 22pts

class ScoreManager:
    def __init__(self):
        self.points = 0
        self.fails = []
        self.comments = ""
        self.valid = True ## indicate that this is a valid score
    def assert_and_update(self, description, expected, actual, points):
        success = (expected == actual)
        print("TEST %s: %s, expected value:%s, actual value:%s\n\t%d/%d points awarded" %
        ("PASSED" if success else "FAILED", description, expected, actual, points if success else 0, points))
        if success:
            self.points += points
        else:
            self.fails.append("%s (-%d)" % (description, points))
        return success
    def finalize(self):
        if len(self.fails) == 0:
            self.comments = "All tests passed."
        else:
            self.comments = "Failed tests: " + "; ".join(self.fails)
        return self.valid, self.points, self.comments


def main(war_path):
    manager = ScoreManager()
    try:
        run_war(war_path, manager)
    except:
        manager.valid = False # invalid grading; our fault.
        print(traceback.format_exc())
    res = manager.finalize()
    print(res)
    return res

# get the downloaded ".war" file (at absolute path) running.
def run_war(abs_file_path, manager):
    res = subprocess.run(['lsof', '-i', ':8080'], shell=True).stdout
    # assert res.returncode == 0, "port 8080 is occupied?"

    cwd = os.getcwd()
    print(f"Extracting from {abs_file_path}")
    test_dir = "__tester_temp"
    if os.path.exists(test_dir):
        shutil.rmtree(test_dir)
    os.makedirs(test_dir)
    os.chdir(test_dir)
    # shutil.copy("../pom.xml", ".") # should use the student's pom.xml instead?
    target_dir = "target/search-api-1.0-SNAPSHOT"
    os.makedirs(target_dir)
    os.chdir(target_dir)
    subprocess.run(['jar', 'xvf', abs_file_path])
    os.chdir("../..")
    shutil.copy("target/search-api-1.0-SNAPSHOT/META-INF/maven/edu.northwestern.ssa/search-api/pom.xml", ".")

    print("Maven is about to run. This may take a while the first time...")
    completed_proc = subprocess.run(MVN + ' compile', shell=True)
    succ = manager.assert_and_update("java code compile return code", 0, completed_proc.returncode, POINT_COMPILE_SUCCESS)
    if succ:
        proc = subprocess.Popen(MVN + " tomcat7:run-war", env=ENV_VARS, shell=True)
        time.sleep(50) # wait for the local host to start
        # input("manual test. press any key to continue!")
        try:
            test_status_code(manager)
            test_queries_exact(manager)
        except:
            manager.valid = False # invalid grading; our fault.
            print(traceback.format_exc())
        manager.assert_and_update("java code executes without crashing", True, proc.poll() is None, POINT_EXEC_SUCCESS)
        if proc.poll() is None:
            proc.terminate() ## We have to execute this line no matter what, otherwise the port 8080 would leak!

    os.chdir("..")
    assert os.getcwd() == cwd, "Back to the original folder?"
    return

# test if returning correct status code
def test_status_code(manager):
    for query in ["", "?count=5"]:
        results = request_and_check_timeout(LOCAL_HOST + API_PATH + query)
        if results is None:
            manager.assert_and_update('timeout "%s"' % query, True, False, POINT_CORRECT_STATUS_CODE)
        else:
            manager.assert_and_update('status code "%s"' % query, 400, results.status_code, POINT_CORRECT_STATUS_CODE)
        time.sleep(0.5)
    for ok_query in ["?query=hello", "?query=billie%20eilish&count=8&offset=10&language=en"]:
        results = request_and_check_timeout(LOCAL_HOST + API_PATH + ok_query)
        if results is None:
            manager.assert_and_update('timeout "%s"' % ok_query, True, False, POINT_CORRECT_STATUS_CODE)
        else:
            manager.assert_and_update('status code "%s"' % ok_query, 200, results.status_code, POINT_CORRECT_STATUS_CODE)
        time.sleep(0.5)

# test queries against Prof's implementation
def test_queries_exact(manager):
    queries = [
      "?query=thisShouldReturnZeroArticles",
      "?query=0xa",
      "?query=0x2",
      "?query=evanston",
      "?query=scalable",
      "?query=hello&count=1",
      "?query=covid19&language=de"
      "?query=anaconda&offset=100",
      "?query=anaconda&count=1",
      "?query=anaconda&offset=10&count=5",
      "?query=anaconda&language=en",
      "?query=anaconda&date=2020-04-12",
      "?query=anaconda&date=2020-04-13&language=en",
      "?query=billie%20eilish&count=8&offset=200",
      "?query=taylor%20swift&count=2&offset=10&language=en&date=2020-04-15",
      "?query=first%20blood&count=5&date=2020-04-11",
      "?query=bench%20press&count=6&language=en",
      "?query=bitcoin%20ethereum%20litecoin&date=2020-04-16",
      "?query=social%20distancing&language=en&offset=250",
      "?query=stay%20at%20home&date=2020-04-12",
      "?query=Northwestern&offset=1024",
      "?query=2020-04-16&language=ru&date=2020-04-16",
      "?query=query&language=fi",
    ]
    for i in range(len(queries)):
        query = queries[i]
        test_response = request_and_check_timeout(LOCAL_HOST + API_PATH + query)
        if test_response is None:
            manager.assert_and_update('timeout "%s"' % query, True, False, POINT_EXACT_SEARCH_RESULT)
            time.sleep(0.5)
            continue
        ref_response = requests.get(REFERENCE_HOST + API_PATH + query)
        assert ref_response.status_code == 200, "Professor's backend not working?"
        try:
            test_json = test_response.json()
        except:
            manager.assert_and_update('bad json format "%s"' % query, True, False, POINT_EXACT_SEARCH_RESULT)
        else:
            ref_json = ref_response.json()
            manager.assert_and_update('exact match "%s"' % query, True, match_json(test_json, ref_json), POINT_EXACT_SEARCH_RESULT)
        time.sleep(0.5)

# helper function
def match_json(test, ref):
    result = True
    try:
        # just count the number of articles and required keys in it:
        if len(test) != len(ref): raise
        if test["returned_results"] != ref["returned_results"]: raise
        if min(10000, test["total_results"]) != min(10000, ref["total_results"]): raise # handling >=10000
        test_articles = test["articles"]
        ref_articles = ref["articles"]
        if len(test_articles) != len(ref_articles): raise
        for i in range(len(ref_articles)):
            t = test_articles[i]
            r = ref_articles[i]
            if len([k for k in ["title", "url", "txt"] if k not in t.keys()]) > 0: raise
            if len([k for k in t.keys() if k not in ["title", "url", "txt", "date", "lang"]]) > 0: raise
            # if there is only 1 article, then strictly match it
            if len(ref_articles) > 1: continue
            for k in ["title", "url", "txt"]:
                ratio = SequenceMatcher(None, t[k], r[k]).ratio()
                print(ratio)
                if ratio < 0.5: raise # language weirdos...
            for k in ["date", "lang"]: # handling None vs. missing
                if k not in r.keys() or r[k] is None:
                    if k in t.keys() and t[k] is not None: raise
                else:
                    if k not in t.keys() or t[k] != r[k]: raise
    except:
        result = False
    return result
    
def request_and_check_timeout(url, timeout=10):
    try:
        r = requests.get(url, timeout=timeout)
    except requests.exceptions.Timeout:
        r = None
    return r

if __name__ == '__main__':
    main(WAR_ABS_PATH)
