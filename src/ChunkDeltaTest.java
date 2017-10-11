import static org.junit.Assert.*;

import java.io.File;

import org.jnbt.CompoundTag;
import org.jnbt.NBTUtils;
import org.junit.Test;

import com.cusackj.mcad.delta.ChunkDelta;
import com.cusackj.mcad.delta.SectionDelta;
import com.cusackj.mcad.model.Chunk;
import com.cusackj.mcad.model.Region;


public class ChunkDeltaTest {

	@Test
	public void test() {

		Region r = new Region(new File("source/r.0.0.mca"));
		Region r2 = new Region(new File("destination/r.0.0.mca"));
		
		Chunk sourceC = r.getChunk(0, 4);
		Chunk destC = r2.getChunk(0, 4);
		
		Chunk sourceMutate = r.getChunk(0, 4);
		
		ChunkDelta cd = new ChunkDelta(sourceC, destC);
		
		cd.applyDelta(sourceMutate);
		
		
		assertEquals(NBTUtils.writeTagToBytes(destC.getTag()).length, NBTUtils.writeTagToBytes(sourceMutate.getTag()).length);
	}

}
