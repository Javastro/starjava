This directory contains some experimental support for trying to build the starjava collection with maven. 

The attempt it is to build at least the subset that is required for ttools in maven.

Note that the pom in this directory and the subdirectory will need to be manually uploaded to any repository

It also depends on a collection of special 3rd party jars that have been collected in the nexus repository

http://agdevel.jb.man.ac.uk:8080/nexus/content/repositories/thirdparty/uk/ac/starlink-third/


The standard qbuild could try <https://maven.apache.org/ant-tasks/>

Use of properties
-----------------
The current solution tries to use properties to set versions - it seems to mostly work (see later) -
however, various utilities and advice forbids this - an alternative would be to define version
both in bom and in each individual pom - not too bad.




Version Numbering
-----------------

The starjava numbering has  3.1-1, 3.1-2

still not entirely sure that they are considered chronological by maven see
https://stackoverflow.com/questions/13004443/how-does-maven-sort-version-numbers


Updating
--------

   git checkout master
   git pull upstream master
   git push --follow-tags
   git checkout maven3support
   

look for versions that are different (tags will be different on each release)

   find . -name lib -print0 |xargs -0 git diff stil-3.2 stil-3.3-3 
   find . -name build.xml -print0 |xargs -0 git diff stil-3.2 stil-3.3-3 
   find . -name .properties -print0 |xargs -0 git diff stil-3.2 stil-3.3-3 