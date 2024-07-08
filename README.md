# totetete

## What is it?

It connects to a jvm via java debug interface (JDI) and steps through your code, displaying or recording each line.

## Why

1. record working and broken test runs, diff and pinpoint exactly the line where things diverge
2. learn what the app is actually doing

## How to use it?

1. add "-Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=5005" to jvm options of your app (`:jvm-opts` vector in profile.clj for leiningen projects)
2. create and start a thread named "totetete-start" to start the recording (it doesn't need to do any work, it's just to catch a thread start event with this name). it is an optimization to delay instrumentation until your app is initialized, as instrumentation brings a big performance hit.
3. create and start a thread named "totetete-stop" to stop the recording and remove instrumentation

## Didn't you already write jdi-steprecorder?

I did, and then removed it due unclear code ownership (I wrote it during work hours at Atlassian, so while I was never explicitly tasked to make it, I made it to debug the project I worked on. This is a clean slate reimplementation.
