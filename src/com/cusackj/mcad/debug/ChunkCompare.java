package com.cusackj.mcad.debug;

import java.util.Arrays;

public class ChunkCompare {
	static byte[][] byteCompares = new byte[32*32][];
	public static void compareBytes(int x, int y, byte[] bytes){
		if(byteCompares[x + y*32]==null){
			byte[] copy = new byte[bytes.length];
			System.arraycopy(bytes, 0, copy	, 0, bytes.length);
			byteCompares[x + y*32] = copy;
		}else{
			if(bytes.length== byteCompares[x + y*32].length){
				System.out.println(x + " " + y + " are equal");
			}else{
				System.err.println(x + " " + y + " are NOT equal");
			}
		}
	}
}
