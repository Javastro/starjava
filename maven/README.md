This directory contains some experimental support for trying to build the starjava collection with maven. 

The attempt it is to build at least the subset that is required for ttools in maven.

Note that the pom in this directory and the subdirectory will need to be manually uploaded to any repository

It also depends on a collection of special 3rd party jars that have been collected in the nexus repository

http://agdevel.jb.man.ac.uk:8080/nexus/content/repositories/thirdparty/uk/ac/starlink-third/


The standard qbuild could try <https://maven.apache.org/ant-tasks/>

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