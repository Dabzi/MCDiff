package com.cusackj.mcad.model;

import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.NBTUtils;

public class Chunk {
	private CompoundTag mTag;
	private long mLastModified = 0;
	private int mX;
	private int mZ;
	
	public Chunk(CompoundTag tag,int x, int z, long lastModified){
		mX = x;
		mZ = z;
		mTag = tag;
		
		
		if(NBTUtils.getXPosFromChunkTag(tag)!=x){
			System.out.println(x + " != " + NBTUtils.getXPosFromChunkTag(tag) + " !!!!");
		}
		
		mLastModified = lastModified;
	}

	public CompoundTag getTag() {
		return mTag;
	}

	public void setTag(CompoundTag mTag) {
		this.mTag = mTag;
	}

	public long getLastModified() {
		return mLastModified;
	}

	public void setLastModified(long mLastModified) {
		this.mLastModified = mLastModified;
	}

	public int getX() {
		return mX;
	}

	public void setX(int mX) {
		this.mX = mX;
	}

	public int getZ() {
		return mZ;
	}

	public void setZ(int mZ) {
		this.mZ = mZ;
	}

	
}
