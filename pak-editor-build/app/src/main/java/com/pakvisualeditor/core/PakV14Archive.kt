package com.pakvisualeditor.core

import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.math.min

object PakV14Archive {
    private const val FOOTER_SIZE = 45
    private const val BLOCK_SIZE = 65536
    private val headerMaskWords = longArrayOf(
        0xA6D17AB4L,0xD4783A41L,0x1C3F5045L,0x034F4301L,
        0x5AA0D38CL,0xDE5364D3L,0x59CAA8EDL,0x5495C626L,
        0xE09B2584L,0x96D67A0EL,0x1FFBEE0AL,0xB84D0C43L,
        0xD373283CL,0x2513CE0AL,0xC8CF93C4L,0xD8CBD95DL
    )
    private val aesKey = hex("d21bf172e5821c51b7b27cd7100d050c6aab5e47000000000000000000000000")
    private val aesIv = hex("78c14502f29c0a600c42b3246799b78e")

    data class Block(val start:Long,val end:Long,val indexStartPos:Int,val indexEndPos:Int)
    data class Entry(
        val index:Int,val entryStartPos:Int,val entryEndPos:Int,val hashPos:Int,val offsetPos:Int,
        val sizePos:Int,val compressionMethodPos:Int,val compressedSizePos:Int,val contentHash:ByteArray,
        val blockCountPos:Int,val blockSizePos:Int,val encryptedPos:Int,val encryptionFlagPos:Int,val keyIdPos:Int,
        val hash:ByteArray,val offset:Long,val size:Long,val compressionMethod:Int,val compressedSize:Long,
        val blocks:List<Block>,val blockSize:Int,val encrypted:Boolean,val encryptionFlag:Int,val keyId:Int
    )
    data class Parsed(
        val file:File,val rawPak:ByteArray,val decodedIndex:ByteArray,val entries:List<Entry>,val indexOffset:Long,
        val encryptedIndexSize:Int,val decodedIndexSha1:ByteArray,val footer:ByteArray,val postIndexOpaque:ByteArray
    )
    data class Validation(val valid:Boolean,val entries:Int,val indexSha1Valid:Boolean,val extractedHashesValid:Boolean,val message:String)

    fun parse(file:File):Parsed {
        val inspection=PakHeaderInspector.inspect(file); require(inspection.isSupportedProfile){inspection.summary}
        val raw=file.readBytes(); val h=PakV14HeaderDecoder.decodeHeader(file)
        require(h.encryptedIndex){"This profile expects an encrypted index"}
        require(h.indexOffset>=0 && h.indexSize in 16..Int.MAX_VALUE.toLong()); require(h.indexOffset+h.indexSize<=raw.size)
        val encIndex=raw.copyOfRange(h.indexOffset.toInt(),(h.indexOffset+h.indexSize).toInt())
        val decoded=decryptIndex(encIndex); val actualHash=MessageDigest.getInstance("SHA-1").digest(decoded)
        require(actualHash.toHexLower().equals(h.expectedIndexSha1,true)){"Index SHA-1 mismatch"}
        val c=ByteCursor(decoded); val mountLen=c.i32(); require(mountLen in 1..4096); c.skip(mountLen)
        val count=c.i32(); require(count in 1..100000); val entries=ArrayList<Entry>(count)
        repeat(count){idx ->
            val start=c.pos; val hashPos=c.pos; val hash=c.bytes(20); val offsetPos=c.pos; val off=c.i64()
            val sizePos=c.pos; val size=c.i64(); val compPos=c.pos; val comp=c.i32(); val compSizePos=c.pos; val compSize=c.i64()
            c.u8(); val contentHash=c.bytes(20); var blockCountPos=-1; val blocks=mutableListOf<Block>()
            if(comp!=0){ blockCountPos=c.pos; val n=c.i32(); require(n in 1..100000); repeat(n){ val sp=c.pos; val bs=c.i64(); val ep=c.pos; val be=c.i64(); blocks+=Block(bs,be,sp,ep) } }
            val blockSizePos=c.pos; val blockSize=c.i32(); val encryptedPos=c.pos; val encrypted=c.u8()!=0
            val encFlagPos=c.pos; val encFlag=c.i32(); val keyIdPos=c.pos; val keyId=c.i32()
            entries+=Entry(idx,start,c.pos,hashPos,offsetPos,sizePos,compPos,compSizePos,contentHash,blockCountPos,blockSizePos,encryptedPos,encFlagPos,keyIdPos,hash,off,size,comp,compSize,blocks,blockSize,encrypted,encFlag,keyId)
        }
        val footerStart=raw.size-FOOTER_SIZE; val oldPostStart=(h.indexOffset+h.indexSize).toInt(); require(oldPostStart<=footerStart)
        return Parsed(file,raw,decoded,entries,h.indexOffset,h.indexSize.toInt(),actualHash,raw.copyOfRange(footerStart,raw.size),raw.copyOfRange(oldPostStart,footerStart))
    }

    fun extract(parsed:Parsed,entry:Entry):ByteArray {
        if(entry.compressionMethod==0){
            val raw=parsed.rawPak.copyOfRange(entry.offset.toInt(),(entry.offset+entry.size).toInt())
            return if(entry.encrypted) SpBlockCodec.decryptSp(raw).copyOf(entry.size.toInt()) else raw
        }
        require(entry.compressionMethod==1){"Only zlib entries are supported in this profile"}
        require(entry.encrypted && entry.encryptionFlag==16){"Only SP-encrypted blocks are supported"}; require(entry.blockSize in 1..BLOCK_SIZE)
        val out=ByteArrayOutputStream(entry.size.toInt()); var produced=0L
        for(block in entry.blocks){
            val stored=(block.end-block.start).toInt(); require(stored>0)
            val enc=parsed.rawPak.copyOfRange(block.start.toInt(),block.end.toInt()); val expected=min(entry.blockSize.toLong(),entry.size-produced).toInt()
            val decPadded=SpBlockCodec.decryptSp(enc); val actualCompressed=zlibStreamLength(decPadded)
            val plain=SpBlockCodec.decryptSpZlib(enc,actualCompressed,expected); out.write(plain); produced+=plain.size
        }
        return out.toByteArray().also{require(it.size.toLong()==entry.size)}
    }

    fun extractAll(parsed:Parsed):List<ByteArray> = parsed.entries.map{extract(parsed,it)}

    fun rebuildSameLayout(parsed:Parsed,replacements:Map<Int,ByteArray>,output:File):File {
        val newIndex=parsed.decodedIndex.copyOf(); val dataOut=ByteArrayOutputStream(parsed.indexOffset.toInt().coerceAtLeast(1024))
        for(entry in parsed.entries){
            val plain=replacements[entry.index]?:extract(parsed,entry); require(plain.size.toLong()==entry.size){"Entry ${entry.index}: replacement must remain ${entry.size} bytes (got ${plain.size})"}
            val newOffset=dataOut.size().toLong()
            if(entry.compressionMethod==0){
                val stored=if(entry.encrypted) SpBlockCodec.encryptSp(plain) else plain
                dataOut.write(stored); val hash=MessageDigest.getInstance("SHA-1").digest(plain); hash.copyInto(newIndex,entry.hashPos)
                newIndex.putI64LE(entry.offsetPos,newOffset); newIndex.putI64LE(entry.sizePos,plain.size.toLong()); newIndex.putI64LE(entry.compressedSizePos,plain.size.toLong())
            } else {
                require(entry.compressionMethod==1); val expectedBlocks=(plain.size+entry.blockSize-1)/entry.blockSize; require(expectedBlocks==entry.blocks.size)
                val newBlocks=ArrayList<Pair<Long,Long>>(expectedBlocks); var p=0
                while(p<plain.size){ val e=min(p+entry.blockSize,plain.size); val encoded=SpBlockCodec.compressZlibSp(plain.copyOfRange(p,e),9); val s=dataOut.size().toLong(); dataOut.write(encoded.encryptedPadded); newBlocks+=s to dataOut.size().toLong(); p=e }
                val hash=MessageDigest.getInstance("SHA-1").digest(plain); hash.copyInto(newIndex,entry.hashPos); newIndex.putI64LE(entry.offsetPos,newOffset); newIndex.putI64LE(entry.sizePos,plain.size.toLong()); newIndex.putI64LE(entry.compressedSizePos,dataOut.size().toLong()-newOffset)
                newBlocks.forEachIndexed{bi,(s,e)->newIndex.putI64LE(entry.blocks[bi].indexStartPos,s); newIndex.putI64LE(entry.blocks[bi].indexEndPos,e)}
            }
        }
        val newData=dataOut.toByteArray(); val newIndexOffset=newData.size.toLong(); val newIndexHash=MessageDigest.getInstance("SHA-1").digest(newIndex); val encryptedIndex=encryptIndex(newIndex)
        require(encryptedIndex.size==parsed.encryptedIndexSize){"Encrypted index size changed"}
        val footer=parsed.footer.copyOf(); encodeFooter(footer,newIndexOffset,encryptedIndex.size.toLong(),newIndexHash,true)
        output.parentFile?.mkdirs(); output.outputStream().buffered().use{it.write(newData);it.write(encryptedIndex);it.write(parsed.postIndexOpaque);it.write(footer)}; return output
    }

    fun validate(file:File):Validation = runCatching{
        val p=parse(file); var hashes=true; p.entries.forEach{e-> val plain=extract(p,e); if(!MessageDigest.getInstance("SHA-1").digest(plain).contentEquals(e.hash)) hashes=false }
        Validation(true,p.entries.size,true,hashes,if(hashes)"PAK valid; index and entry SHA-1 checks passed" else "Index valid, but an entry hash differs")
    }.getOrElse{Validation(false,0,false,false,it.message?:it.toString())}

    private fun decryptIndex(enc:ByteArray):ByteArray { val c=Cipher.getInstance("AES/CBC/PKCS5Padding"); c.init(Cipher.DECRYPT_MODE,SecretKeySpec(aesKey,"AES"),IvParameterSpec(aesIv)); return c.doFinal(enc) }
    private fun encryptIndex(plain:ByteArray):ByteArray { val c=Cipher.getInstance("AES/CBC/PKCS5Padding"); c.init(Cipher.ENCRYPT_MODE,SecretKeySpec(aesKey,"AES"),IvParameterSpec(aesIv)); return c.doFinal(plain) }
    private fun encodeFooter(footer:ByteArray,indexOffset:Long,indexSize:Long,indexSha1:ByteArray,encrypted:Boolean){
        val offMask=(headerMaskWords[0] shl 32) or headerMaskWords[1]; val sizeMask=(headerMaskWords[10] shl 32) or headerMaskWords[11]
        footer[0]=(((if(encrypted)1 else 0) xor (headerMaskWords[3].toInt() and 0xFF)) and 0xFF).toByte()
        for(i in 0 until 5) footer.putU32LE(9+i*4,indexSha1.getU32LE(i*4) xor headerMaskWords[4+i])
        footer.putI64LE(29,indexSize xor sizeMask); footer.putI64LE(37,indexOffset xor offMask)
    }
    private fun zlibStreamLength(padded:ByteArray):Int { val pad=padded.last().toInt() and 0xFF; return if(pad in 1..16 && pad<=padded.size && padded.takeLast(pad).all{(it.toInt() and 0xFF)==pad}) padded.size-pad else padded.size }
    private fun hex(s:String):ByteArray = ByteArray(s.length/2){i->s.substring(i*2,i*2+2).toInt(16).toByte()}
}
