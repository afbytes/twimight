This document lists the basic requirement to start developing Android applications and some
tips in order to setup the system. From downloading and compiling the source to installing the application. We also include some general background about developing for Android.

**If you want to contribute, get in touch with us at @twimight!**

# Requirements #
This is a list of tools and libraries you have to install and be familiar with before starting:
  * [Eclipse IDE](http://www.eclipse.org)
  * Java SDK
  * [Android SDK](http://developer.android.com/sdk/installing.html)
  * [ADT plugin for Eclipse](http://developer.android.com/sdk/eclipse-adt.html)
  * OAuth Libraries
  * [Twitter APIs](http://www.winterwell.com/software/jtwitter/javadoc/index.html?winterwell/jtwitter/Twitter.html)
  * [PhotoView](https://github.com/chrisbanes/PhotoView) (add as library project)
  * svn

The Java and Android SDK (Standard Development Kit) as well as the ADT (Android Development Tools) plugin are necessary to build the application including the User Interface. The most common and convenient IDE is Eclipse. For Android development the ADT Eclipse plugin has to be installed which provides a rich and powerful environment to build apps. To be able to login into Twitter the application uses to used the OAuth Signpost Libraries providing the basic authentication functionalities. After successful login, Twimight communicates with the Twitter servers using the Open Source JTwitter APIs.

# Tips #
Before getting your hands dirty, read the following:
  * We strongly recommend using the Eclipse IDE (Integrated Development Environment) as Java Editor.
  * Install the Android SDK and the ADT plugin 6 which are the standard environment to develop Android applications.
  * Download the essential components for the Android SDK as well as setup the AVD Manager from the graphical UI of the SDK itself.
  * If developing on Linux, install the USB cable drivers.
  * Follow the [Hello World tutorial](http://developer.android.com/resources/tutorials/hello-world.html).

# Getting the Twimight code #
After installing Android SDK, Eclipse and ADT, [checkout](http://code.google.com/p/twimight/source/checkout) the Twimight code.
Then, in Eclipse, import the project: "File" -> "Import.." -> "Existing Projects into Workspace". Select the right root directory and hit "Finish".

## Obfuscator ##
When you have imported the project into your workspace you will receive the error **"Obfuscator cannot be resolved"**. The Obfuscator class is intentionally missing in the repository because it contains credentials which we cannot make public. To fix this error create the following class:
```
package ch.ethz.bluetest.credentials;

public class Obfuscator {

  public static String getKey(){
    return "your Twitter consumer key";
  }
	
  public static String getSecret(){
    return "your Twitter consumer secret";
  }
	
  public static String getCrittercismId(){
    return "your Crittercism ID or empty string";
  }
}
```

You can get your own Twitter consumer key and secret by creating a Twitter application at https://dev.twitter.com.

Crittercism is used to collect error data. It is however not essential to the functionality of Twimight. Therefore you can have `getCrittercismId()` return an empty string. Alternatively you can get your own Crittercism ID by signing up at https://www.crittercism.com.

<a href='Hidden comment: 
= General Android Concepts =
Android applications are written in Java. The Android SDK is used compile the code into an Android package, a file with the .apk extension. This is the file that Android devices use to install the application.

The following is a list of general Android concepts, which you absolutely have to be familiar with.

== Components ==
* *Activity*: An activity represents a single user interface. Though the activities work together to form a complete application, each one is independent of the others. As such, a different application can start any one of these activities. An activity is implemented as a subclass of Activity .
* *Service*: A service is a component that runs in the background to execute some heavy or long operations. It does not provide any user interface. The most common examples are a background music player or simply a service that access the network. Another component, such as an activity, can start the service and let it run or bind to it in order to interact with it. A service class extends Service.
* *Content Provider*: A content provider allows to share application data. For instance it is possible to share a SQLite database, or any other persistent storage location. Through the content provider, other applications can query or even modify the data (if the content provider allows it).
* *Broadcast receiver*: A broadcast receiver is a component that listens for intents broadcasted in the system. A broadcast receiver is implemented as a subclass of BroadcastReceiver and each broadcast is delivered as an Intent object.

== Intents ==
Activities, services, and broadcast receiver are activated by an Intent. It allows to bind individual components to each other at runtime. An intent is created with an Intent object, and it can be either explicit or implicit. For activities and services, an intent defines the action to perform. For example, an intent might convey a request to open another activity in the application. In some cases, you can start an activity to receive a result, in which case, the activity also returns the result in an Intent.

== Activity Lifecycle ==
[http://developer.android.com/guide/topics/fundamentals/activities.html Activities]

== The Manifest ==
Every application has a manifest le. It stores the essential information about the application. Every component must be declared there along with all the permissions required to run. Moreover it stores the following information:
* The name of the Java package that uniquely identies the application.
* The processes that will host application components.
* Declares the minimum level of the Android API that the application requires.
* It lists the libraries that the application uses.
'></a>