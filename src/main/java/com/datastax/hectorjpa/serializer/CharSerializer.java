package com.datastax.hectorjpa.serializer;

import java.nio.ByteBuffer;

import me.prettyprint.cassandra.serializers.AbstractSerializer;

/**
 * Uses Char Serializer
 * 
 * @author Todd Nine
 */
public class CharSerializer extends AbstractSerializer<Character> {

	private static final CharSerializer instance = new CharSerializer();

	public static CharSerializer get() {
		return instance;
	}

	@Override
	public ByteBuffer toByteBuffer(Character obj) {
		ByteBuffer buffer = ByteBuffer.allocate(Character.SIZE / Byte.SIZE);

		buffer.putChar(obj);
		buffer.rewind();

		return buffer;
	}

	@Override
	public Character fromByteBuffer(ByteBuffer bytes) {
		if (bytes == null) {
			return null;
		}
		return bytes.getChar();

	}

}
