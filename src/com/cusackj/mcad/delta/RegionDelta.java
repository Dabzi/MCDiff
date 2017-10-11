package com.cusackj.mcad.delta;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.jnbt.CompoundTag;
import org.jnbt.IntTag;
import org.jnbt.NBTUtils;
import org.jnbt.Tag;

import com.cusackj.mcad.model.Chunk;
import com.cusackj.mcad.model.Region;

/** @author Jack Cusack */
public class RegionDelta {

	public static void main(String[] args) throws Exception {
		if (args.length < 1) {
			System.out.println("Path to .mca file required!");
		}

		File f = new File(args[0]);
		if (!f.exists()) {
			System.out.println("Could not find " + f.getAbsolutePath() + ".");
			return;
		}

		File f2 = null;
		if (args.length > 1) {
			f2 = new File(args[1]);
			if (!f.exists()) {
				System.out.println("Could not find " + f2.getAbsolutePath() + ".");
				return;
			}
		}

		Region r1 = new Region(f);
		if (f2 != null) {
			Region r2 = new Region(f2);
			RegionDelta delta = new RegionDelta(r1, r2);
			
			File mcad = new File(f2.getName() + "d");
			delta.writeToFile(mcad);
			
			System.out.println("Writing expected output");
			r2.write(new File("ExpectedOutput"));
			System.out.println("Writing actual output");
			delta.applyDelta(r1).write(new File(f.getName()));;
			
		}
	}

	/** The timestamp on the region file for the source region. */
	private long mSourceTimestamp;

	/** The timestamp on the region file for the destination region. */
	private long mDestTimestamp;
	/** Represents which chunks have changed.
	 * Where a bit is set represents a changed chunk, and an entry in mChunkDeltas */
	private byte[] mChangedChunkBits = new byte[128];

	/** List of ChunkDeltas, length is however many set bits there are in mChangedChunkBits. */
	private List<ChunkDelta> mChunkDeltas = new ArrayList<>();

	public RegionDelta(InputStream inputStream) throws IOException {
		InputStream in = new GZIPInputStream(inputStream);
		ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
		while (in.available() > 0) {
			byteArrayOut.write(in.read());
		}

		ByteBuffer bb = ByteBuffer.wrap(byteArrayOut.toByteArray());

		mSourceTimestamp = bb.getLong();
		mDestTimestamp = bb.getLong();
		bb.get(mChangedChunkBits);

		mChunkDeltas = new ArrayList<>();

		while (bb.remaining() >= 4) {

			int chunkSize = bb.getInt();
			byte[] chunkBytes = new byte[chunkSize];
			bb.get(chunkBytes);
			ChunkDelta d = new ChunkDelta(chunkBytes);
			mChunkDeltas.add(d);
		}

	}

	public RegionDelta(long sourceTime, long destTime, List<ChunkDelta> chunkDeltas, byte[] changedChunkBits) {
		mSourceTimestamp = sourceTime;
		mDestTimestamp = destTime;
		mChunkDeltas = chunkDeltas;
		mChangedChunkBits = changedChunkBits;
	}

	public RegionDelta(Region dst) {
		calculateChunkDeltas(new Region(), dst);
	}

	public RegionDelta(Region src, Region dst) {
		calculateChunkDeltas(src, dst);
	}

	private void calculateChunkDeltas(Region src, Region dst) {

		// If these two regions are not for the correct space, then an exception should be thrown TODO
		if (!(src.getX() == dst.getX()) || !(src.getZ() == dst.getZ()))
			return;
		int regionX = src.getX();
		int regionZ = src.getZ();

		// For outputting result.
		int changedChunks = 0;
		int newChunks = 0;
		int removedChunks = 0;
		int ignoredChunks = 0;
		int totalPossible = 32 * 32;

		for (int z = regionZ * 32; z < regionZ * 32 + 32; z++) {
			for (int x = regionX * 32; x < regionX * 32 + 32; x++) {
				// Find chunks that exist in each file, and create delta chunks
				if (src.hasChunk(x, z) && dst.hasChunk(x, z)) {
					if (src.getChunkTimestamp(x, z) != dst.getChunkTimestamp(x, z)) {

						CompoundTag sourceChunk = getChunkTag(src, x, z);
						CompoundTag destChunk = getChunkTag(dst, x, z);

						setMaskBit(mChangedChunkBits, x, z);


						ChunkDelta cd = new ChunkDelta(new Chunk(sourceChunk, x, z, src.getChunkTimestamp(x, z)), new Chunk(destChunk, x, z, dst.getChunkTimestamp(x, z)));
						mChunkDeltas.add(cd);
						changedChunks++;
						continue;
					} else {
						ignoredChunks++;
						continue;
					}
				}

				// If region2 has new chunks
				if (!src.hasChunk(x, z) && dst.hasChunk(x, z)) {
					CompoundTag destChunk = getChunkTag(dst, x, z);

					setMaskBit(mChangedChunkBits, x, z);

					ChunkDelta cd = new ChunkDelta(new Chunk(destChunk, x, z, dst.getChunkTimestamp(x, z)));
					mChunkDeltas.add(cd);
					newChunks++;
					continue;
				}
				// If chunks have been removed from region2
				if (src.hasChunk(x, z) && !dst.hasChunk(x, z)) {
					setMaskBit(mChangedChunkBits, x, z);

					ChunkDelta cd = new ChunkDelta();
					mChunkDeltas.add(cd);
					removedChunks++;
					continue;
				}
				totalPossible--;
			}
		}

		// DELTA REPORT
		System.out.println("*********************************************************************************");
		System.out.println(mChunkDeltas.size() + " chunk deltas created out of a possible " + totalPossible);
		System.out.println(changedChunks + " chunks have been changed.");
		System.out.println(newChunks + " chunks have been created");
		System.out.println(removedChunks + " chunks have been removed");
		System.out.println(ignoredChunks + " have been ignored");
		System.out.println("*********************************************************************************");

	}
	
	public void setMaskBit(byte[] array, int x, int z){
		array[(int) Math.floor((x + z * 32) / 8d)] |= 1 << ((x + z * 32) % 8);
		System.out.println("Set bit " + (int) Math.floor((x + z * 32)) + " for " + x + ", "  + z);
	}
	
	public int getXFromMask(int bit){
		return bit % 32;
	}
	
	public int getZFromMask(int bit){
		return (int) Math.floor((bit - getXFromMask(bit)) / 32);
	}

	private CompoundTag getChunkTag(Region r, int x, int z) {
		return r.getChunk(x, z).getTag();
	}

	public Region applyDelta(Region src) throws Exception {
		//TODO CLONE
		
		
		int totalChunks = src.getNumberOfChunks();
		int chunksCreated = 0;
		int chunksChanged = 0;
		int chunksRemoved = 0;
		
		System.out.println("Preparing to process " + mChunkDeltas.size() + " changes to " + totalChunks + " chunks");
		
		int bit = 0;
		for (ChunkDelta cDelta : mChunkDeltas) {
			//Use the bitmask to determine the x+y of this chunk 
			while(!isBitSet(mChangedChunkBits, bit)){
				bit++;
			}
			
			int x = getXFromMask(bit);
			int z = getZFromMask(bit);
			
			
			bit++;
			
			Chunk srcChunk = src.getChunk(x, z);

			if (cDelta.getLengthInBytes() == 0) {
				src.removeChunk(x, z);
				chunksRemoved++;
			} else {
				if (srcChunk == null) {
					// Create an empty chunk, then apply delta to it
					Map<String, Tag> contents = new HashMap<>();
					Map<String, Tag> levelContents = new HashMap<>();
					CompoundTag levelTag = new CompoundTag("Level", levelContents);
					contents.put("Level", levelTag);
					levelTag.getValue().put("xPos", new IntTag("xPos", x));
					levelTag.getValue().put("zPos", new IntTag("zPos", z));
					
					Chunk c = new Chunk(new CompoundTag("", contents), x, z, mDestTimestamp);
					cDelta.applyDelta(c);
					src.setChunk(c);
					chunksCreated++;
				} else {
					// Apply delta to srcChunk
					srcChunk.setLastModified(mDestTimestamp);
					Chunk c = cDelta.applyDelta(srcChunk);
					src.setChunk(c);
					chunksChanged++;
				}
			}
		}
		
		printMergeReport(totalChunks, chunksChanged, chunksCreated, chunksRemoved);
		return src;
	}
	
	private void printMergeReport(int totalChunks, int chunksChanged, int chunksCreated, int chunksRemoved){
		System.out.println("*********************************************************************************");
		System.out.println(totalChunks + " chunks in region file");
		System.out.println(chunksChanged + " chunks were changed.");
		System.out.println(chunksCreated + " chunks were created");
		System.out.println(chunksRemoved + " chunks were removed");
		System.out.println("*********************************************************************************");
	}

	private boolean isBitSet(byte[] target, int bitOffset) {
		int index = (int) Math.floor(bitOffset / 8);
		int bitIndex = bitOffset % 8;
		byte result = (byte) ((target[index] >> bitIndex) & 1);
		return result == 1;
	}

	public void writeToFile(File f) throws IOException {
		if (!f.exists()) {
			System.out.println("Creating " + f.getName() + "...");
			f.createNewFile();
		}
		int length = 16 + 128;
		for (ChunkDelta cd : mChunkDeltas) {
			length += cd.getLengthInBytes() + 4;// +4 because of size field;
		}
		OutputStream out = new GZIPOutputStream(new FileOutputStream(f));
		ByteBuffer buffer = ByteBuffer.allocate(length);

		buffer.putLong(mSourceTimestamp);
		buffer.putLong(mDestTimestamp);
		buffer.put(mChangedChunkBits);

		for (ChunkDelta d : mChunkDeltas) {
			buffer.putInt(d.getLengthInBytes());
			buffer.put(d.getBytes());
		}
		System.out.println("Wrote " + mChunkDeltas.size() + " chunk deltas [" + buffer.array().length + "]");

		out.write(buffer.array());
		out.flush();
		out.close();
		System.out.println("after compression " + f.length() + " bytes");
	}

}
