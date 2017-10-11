package com.cusackj.mcad.delta;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.jnbt.ByteArrayTag;
import org.jnbt.ByteTag;
import org.jnbt.CompoundTag;
import org.jnbt.NBTUtils;
import org.jnbt.Tag;

import com.cusackj.utils.PrintUtils;

public class SectionDelta {
	
	ByteTag mY;
	ByteArrayTag mBlockLight;
	ByteArrayTag mData;
	ByteArrayTag mBlocks;
	ByteArrayTag mSkyLight;
	
	DeltaArray mBlockLightDelta;
	DeltaArray mDataDelta;
	DeltaArray mBlocksDelta;
	DeltaArray mSkyLightDelta;
	
	boolean isEmpty = false;
	
	public SectionDelta(CompoundTag source, CompoundTag dest){
		setDestTags(dest);
				
		byte[] blockLightSource = ((ByteArrayTag) source.getValue().get("BlockLight")).getValue();
		byte[] blockLightDest = mBlockLight.getValue();
		mBlockLightDelta = calculateDeltaArray(blockLightSource, blockLightDest);
		
		byte[] dataSource = ((ByteArrayTag) source.getValue().get("Data")).getValue();
		byte[] dataDest = mData.getValue();
		mDataDelta = calculateDeltaArray(dataSource, dataDest);
		
		byte[] blockSource = ((ByteArrayTag) source.getValue().get("Blocks")).getValue();
		byte[] blockDest = mBlocks.getValue();
		mBlocksDelta = calculateDeltaArray(blockSource, blockDest);
		
		byte[] skyLightSource = ((ByteArrayTag) source.getValue().get("SkyLight")).getValue();
		byte[] skyLightDest = mSkyLight.getValue();
		mSkyLightDelta = calculateDeltaArray(skyLightSource, skyLightDest);
		
		//Assertions that the section is at least equal length, if not identical!
		byte[] sourceBytes = NBTUtils.writeTagToBytes(source);
		CompoundTag copiedSource = (CompoundTag) NBTUtils.bytesToTag(sourceBytes);
		
		applyDelta(copiedSource);
		
		byte[] mutatedBytes = NBTUtils.writeTagToBytes(copiedSource);
		byte[] destBytes = NBTUtils.writeTagToBytes(dest);
		
		if(destBytes.length == mutatedBytes.length){
			System.out.println("******");
			System.out.println("Section delta is valid in length");
			if(Arrays.equals(destBytes, mutatedBytes)){
				System.out.println("and are identical in bytes!!");
			}else{
				System.out.println("but are not identical in bytes! :(");
			}
			System.out.println("******");
		}
	}
	
	public int getY(){
		return mY.getValue();
	}
	
	public SectionDelta(CompoundTag dest){
		setDestTags(dest);
		
		mBlockLightDelta =  createDeltaArray(mBlockLight.getValue());
		mDataDelta =  createDeltaArray(mData.getValue());
		mBlocksDelta =  createDeltaArray(mBlocks.getValue());
		mSkyLightDelta =  createDeltaArray(mSkyLight.getValue());
		
		//Verify identical
		Map<String, Tag> sectionMap = new HashMap<>();

		sectionMap.put("Y", new ByteTag("Y", (byte) getY()));
		sectionMap.put("BlockLight", new ByteArrayTag("BlockLight", new byte[2048]));
		sectionMap.put("Blocks", new ByteArrayTag("Blocks", new byte[4096]));
		sectionMap.put("Data", new ByteArrayTag("Data", new byte[2048]));
		sectionMap.put("SkyLight", new ByteArrayTag("SkyLight", new byte[2048]));

		CompoundTag t = new CompoundTag("", sectionMap);
		applyDelta(t);
		
		byte[] destBytes = NBTUtils.writeTagToBytes(dest);
		byte[] mutatedBytes = NBTUtils.writeTagToBytes(t);
		
		System.out.println("****** (full delta)");
		if(destBytes.length == mutatedBytes.length){
			System.out.println("Section delta is valid in length");
			
		}else{
			System.out.println("Section delta is wrong in length");
		}
		
		if(Arrays.equals(destBytes, mutatedBytes)){
			System.out.println("and are identical in bytes!!");
		}else{
			System.out.println("but are not identical in bytes! :(");
		}
		System.out.println("******");
	}
	
	public SectionDelta(byte[] bytes){
		if(bytes.length==1) {
			mY = new ByteTag("Y", bytes[0]);
			isEmpty = true;
			return;
		}
		ByteBuffer bb = ByteBuffer.wrap(bytes);
		
		mY = new ByteTag("Y", bb.get());
		
		int blockLightLength = bb.getInt() - 256;
		mBlockLightDelta = new DeltaArray();
		mBlockLightDelta.changeArray = new byte[256];
		mBlockLightDelta.changeValues = new byte[blockLightLength];
		bb.get(mBlockLightDelta.changeArray);
		bb.get(mBlockLightDelta.changeValues);
		
		int blocksLength = bb.getInt() - 512;
		mBlocksDelta = new DeltaArray();
		mBlocksDelta.changeArray = new byte[512];
		mBlocksDelta.changeValues = new byte[blocksLength];
		bb.get(mBlocksDelta.changeArray);
		bb.get(mBlocksDelta.changeValues);
		
		int dataLength = bb.getInt() - 256;
		mDataDelta = new DeltaArray();
		mDataDelta.changeArray = new byte[256];
		mDataDelta.changeValues = new byte[dataLength];
		bb.get(mDataDelta.changeArray);
		bb.get(mDataDelta.changeValues);
		
		int skyLightLength = bb.getInt() - 256;
		mSkyLightDelta = new DeltaArray();
		mSkyLightDelta.changeArray = new byte[256];
		mSkyLightDelta.changeValues = new byte[skyLightLength];
		bb.get(mSkyLightDelta.changeArray);
		bb.get(mSkyLightDelta.changeValues);
		
	}
	
	private void setDestTags(CompoundTag dest){
		mY = (ByteTag) dest.getValue().get("Y");
		mBlockLight = (ByteArrayTag) dest.getValue().get("BlockLight");
		mData = (ByteArrayTag) dest.getValue().get("Data");
		mBlocks = (ByteArrayTag) dest.getValue().get("Blocks");
		mSkyLight = (ByteArrayTag) dest.getValue().get("SkyLight");
	}
	
	private DeltaArray calculateDeltaArray(byte[] source, byte[] dest){
		DeltaArray result = new DeltaArray();
		
		result.changeArray = new byte[source.length / 8];
		
		int arrayLength = 0;
		for (int i = 0; i < source.length; i++) {
			if (source[i] != dest[i]) {
				int bit = i % 8;
				result.changeArray[(int) Math.floor(i/8d)] |= 1 << bit;
				arrayLength += 1;
			}
		}
		result.changeValues = new byte[arrayLength];
		int bit = 0;
		for (int i = 0; i < arrayLength; i++) {
			// loop through each bit in mHeightMapChanges
			while (!isBitSet(result.changeArray, bit)) {
				bit++;
			}
			result.changeValues[i] = dest[bit];
			bit++;
		}
		return result;
	}
	
	private DeltaArray createDeltaArray(byte[] dest){
		DeltaArray result = new DeltaArray();
		result.changeArray = new byte[dest.length / 8];
		for(int i = 0 ; i < result.changeArray.length ; i++){
			result.changeArray[i] = (byte) 0xff;
		}
		result.changeValues = new byte[dest.length];
		System.arraycopy(dest, 0, result.changeValues, 0, dest.length);
		return result;
	}
	
	private void applyDeltaArray(DeltaArray da, byte[] dest){
		int bit = 0;
		for(int i = 0; i < da.changeValues.length ; i++){
			while(!isBitSet(da.changeArray, bit)){
				bit++;
			}
			dest[bit] = da.changeValues[i];
			bit++;
		}
	}
	
	private boolean isBitSet(byte[] target, int bitOffset) {
		int index = (int) Math.floor(bitOffset / 8);
		int bitIndex = bitOffset % 8;
		byte result = (byte) ((target[index] >> bitIndex) & 1);
		return result == 1;
	}
	
	public SectionDelta(byte y){
		mY = new ByteTag("Y", y);
		isEmpty =true;
	}
	
	
	private class DeltaArray{
		byte[] changeArray;
		byte[] changeValues;
		
		private int getSize(){
			return changeArray.length + changeValues.length;
		}
	}

	public int getLengthInBytes() {
		if(isEmpty) return 1;
		return 1 + mBlockLightDelta.getSize()+4 + mBlocksDelta.getSize()+4 + mDataDelta.getSize()+4 + mSkyLightDelta.getSize()+4;
	}


	public byte[] getBytes() {
		if(isEmpty) return new byte[]{mY.getValue()};
		ByteBuffer bb = ByteBuffer.allocate(getLengthInBytes());
		
		bb.put(mY.getValue());
		
		bb.putInt(mBlockLightDelta.getSize());
		bb.put(mBlockLightDelta.changeArray);
		bb.put(mBlockLightDelta.changeValues);
		
		bb.putInt(mBlocksDelta.getSize());
		bb.put(mBlocksDelta.changeArray);
		bb.put(mBlocksDelta.changeValues);
		
		
		bb.putInt(mDataDelta.getSize());
		bb.put(mDataDelta.changeArray);
		bb.put(mDataDelta.changeValues);
		
		bb.putInt(mSkyLightDelta.getSize());
		bb.put(mSkyLightDelta.changeArray);
		bb.put(mSkyLightDelta.changeValues);
		
		return bb.array();
	}

	public void applyDelta(CompoundTag sectionTag) {
		sectionTag.getValue().put("Y", mY);
		byte[] blockLightArray = ((ByteArrayTag)sectionTag.getValue().get("BlockLight")).getValue();
		applyDeltaArray(mBlockLightDelta, blockLightArray);
		
		byte[] blockArray = ((ByteArrayTag)sectionTag.getValue().get("Blocks")).getValue();
		applyDeltaArray(mBlocksDelta, blockArray);
		
		byte[] dataArray = ((ByteArrayTag)sectionTag.getValue().get("Data")).getValue();
		applyDeltaArray(mDataDelta, dataArray);
		
		byte[] skyLightArray = ((ByteArrayTag)sectionTag.getValue().get("SkyLight")).getValue();
		applyDeltaArray(mSkyLightDelta, skyLightArray);
	}
}
