package com.axelor.testing;

public class Testing {

  public static void main(String[] args) {

    //    int a[] = {1, 2, 3, 4, 5, 7, 9, 10, 12, 13, 14, 16, 19};
    //    findMissingElement(a);
    double min = Math.min(-2.0, -3.0);
    System.out.print(min);
  }

  public static void findMissingElement(int[] a) {
    for (int i = 0; i < a.length - 1; i++) {
      if (a[i + 1] - a[i] != 1 && a[i + 1] - a[i] > 1) {
        int b = a[i];
        for (int j = 1; j < a[i + 1] - a[i]; j++) {
          b = b + 1;
          System.out.println("The Missing Element is" + b);
        }
      }
    }
  }
}
