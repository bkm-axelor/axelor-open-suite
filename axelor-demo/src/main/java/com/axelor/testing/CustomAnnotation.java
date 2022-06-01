package com.axelor.testing;

import java.lang.reflect.Method;

class CustomAnnotation {

  @MyAnnotation(value = 25)
  public static void myMethod() {
    CustomAnnotation obj = new CustomAnnotation();
    
//    ArrayList<String> newArrayList = Lists.newArrayList("Bikash", "Akash", "Hello");
    
//    Splitter.onPattern(".|,").omitEmptyStrings().splitToList(null);
    
    try {
      Method method = obj.getClass().getMethod("myMethod");
      MyAnnotation annotation = method.getAnnotation(MyAnnotation.class);
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
