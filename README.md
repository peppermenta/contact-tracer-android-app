# contact-tracer-android-app
The ContactTracer App for Android

A Contact Tracing App implementation, that uses Bluetooth Low Energy(BLE). </br>
To be used in conjunction with [contact-tracer-backend](https://github.com/peppermenta/contact-tracer-backend)

### Setup
Set the following variables with the server IP Address

```java
//MainActivity.java
public String apiEndpoint = "<Server IP Address>/api/infected"
```

```java
//LoginActivity.java
public String loginEndpoint = "<Server IP Address>/auth/login"
```

```java
//RegisterActivity.java
public String loginEndpoint = "<Server IP Address>/auth/register"
```

```java
//JWT_Token_Handler.java
//Ensure this JWT Secret is the same as the one used in the backend server
private static final String key = "<Base64 Encoded JWT Secret>"
```
Build and run the app :)


