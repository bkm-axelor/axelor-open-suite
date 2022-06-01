package com.axelor.testing;

import java.lang.reflect.Method;

public class CustomMultiAnnotation {

  @MyAnnotation2(svalue = "Hii", value = 45)
  public static void myMethod() {
    CustomMultiAnnotation obj = new CustomMultiAnnotation();

    try {
      Method method = obj.getClass().getMethod("myMethod");
      MyAnnotation2 annotation = method.getAnnotation(MyAnnotation2.class);
      System.out.println(annotation.svalue());
      System.out.println(annotation.bvalue());
      System.out.println(annotation.value());
    } catch (NoSuchMethodException e) {
      System.out.println("Method not found !!");
      e.printStackTrace();
    }
  }

  public static void main(String args[]) {
    myMethod();
  }

}
