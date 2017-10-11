package com.cusackj.utils;

public class PrintUtils {
	public static void printBytes(byte[] array){
		System.out.println();
		for(byte b : array){
			System.out.print(String.format("%02X",b));
		}
	}
}
