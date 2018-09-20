# opencv-rectangle-detector

HOWTO build:

you need this HOWTO in case if you got errors like

```
/usr/lib/x86_64-linux-gnu/libstdc++.so.6 version glibcxx_3.4.22' not found
```

with libopencv_javaXXX.so that in maven dist, and you don't have possibility to use latest libc6 in your linux system (i.e. fixed version of Debian)

 

1. install dependencies

```
apt-get install git python python3 ant cmake build-essential libgtk2.0-dev pkg-config python-dev python-numpy libtbb2 libtbb-dev libdc1394-22-dev -y
```

2. prepare:

```
git clone git://github.com/opencv/opencv.git
cd opencv
git checkout 3.2.0
mkdir build
cd build
```

3. set java home

```
export JAVA_HOME=/usr/lib/jvm/java-8-oracle
```


4. prepare build, add 3dparty modules (like support of PNG images)

```
cmake -D BUILD_SHARED_LIBS=OFF -D WITH_ZLIB=ON -D BUILD_ZLIB=ON -D WITH_OPENEXR=ON -D BUILD_OPENEXR=ON -D WITH_TBB=ON -D WITH_JASPER=ON -D BUILD_JASPER=ON -D WITH_PNG=ON -D BUILD_PNG=ON -D WITH_JPEG=ON -D BUILD_JPEG=ON -D WITH_TIFF=ON -D BUILD_TIFF=ON -D BUILD_IPP_IW=ON -D WITH_ITT=ON -D BUILD_ITT=ON -D WITH_1394=ON ..
```

where  
BUILD_XXX - build using dependency from opencv/3dparty/XXX folder  
WITH_XXX - include dependency into build

5. make sure that output (at the end) has following:

```
-- Java:
-- ant: /usr/bin/ant (ver 1.9.4)
-- JNI: /usr/lib/jvm/java-8-oracle/include /usr/include /usr/lib/jvm/java-8-oracle/include
-- Java wrappers: YES
-- Java tests: YES
```

6. start build (using 8 threads)

```
make -j8
```

7. on success check following folders:

```
bin/opencv-320.jar
lib/libopencv_java320.so
```

useful links:
https://advancedweb.hu/2016/03/01/opencv_ubuntu/
https://docs.opencv.org/trunk/d9/d52/tutorial_java_dev_intro.html