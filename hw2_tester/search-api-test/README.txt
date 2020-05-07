Murphy instructions

If you haven't set up Maven, do the following. Otherwise, skip these two steps.

$ wget https://archive.apache.org/dist/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz
$ tar xzvf apache-maven-3.6.2-bin.tar.gz

Run script:

$ scl enable rh-python36 bash
$ cd search-api-test

... copy your search-api-1.0-SNAPSHOT.war into this folder ...

$ python3.6 -m venv virtualenv
$ . virtualenv/bin/activate

Note: pip install only needs to be run once

$ pip install -r requirements.txt

Important: before running testscript.py, please change variables
JAVA_HOME, MVN and WAR_ABS_PATH per instructions inside the script

$ python testscript.py

Note: this script has been tested on Python3.6.9 and Python3.7.3.
