package com.datastax.hectorjpa.meta;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.persistence.MappedSuperclass;

import org.apache.openjpa.meta.ClassMetaData;

import com.datastax.hectorjpa.serialize.EmbeddedSerializer;
import com.datastax.hectorjpa.store.CassandraClassMetaData;
import com.datastax.hectorjpa.store.EntityFacade;

/**
 * Cache for holding all meta data
 * 
 * @author Todd Nine
 * 
 */
public class MetaCache {

	private final ConcurrentMap<ClassMetaData, EntityFacade> metaData = new ConcurrentHashMap<ClassMetaData, EntityFacade>();

	private final ConcurrentMap<String, CassandraClassMetaData> discriminators = new ConcurrentHashMap<String, CassandraClassMetaData>();

	/**
	 * Create a new meta cache for classes
	 * 
	 */
	public MetaCache() {

	}

	/**
	 * Get the entity facade for this class. If it does not exist it is created
	 * and added to the cache. Will return null if the given class meta data
	 * cannot be directly persisted. Generally only applies to @MappedSuperclass
	 * classes
	 * 
	 * @param meta
	 * @return
	 */
	public EntityFacade getFacade(ClassMetaData meta,
			EmbeddedSerializer serializer) {

		CassandraClassMetaData cassMeta = (CassandraClassMetaData) meta;

		EntityFacade facade = metaData.get(cassMeta);

		if (facade != null) {
			return facade;
		}

		if (cassMeta.isAbstract()) {
			return null;
		}

		//embedded object, nothing to do 
		if (cassMeta.isEmbeddedOnly()) {
			return null;
		}

		facade = new EntityFacade(cassMeta, serializer);

		metaData.putIfAbsent(cassMeta, facade);

		String discriminatorValue = cassMeta.getDiscriminatorColumn();

		if (discriminatorValue != null) {

			discriminators.putIfAbsent(discriminatorValue, cassMeta);
		}

		// if we have super or subclasses, they could be loaded at any point via
		// query without a persist. As a consequence
		// we must eagerly load all children and parents.
		for (ClassMetaData subClass : cassMeta.getPCSubclassMetaDatas()) {
			getFacade(subClass, serializer);
		}

		// load the parent
		ClassMetaData parentMeta = cassMeta.getPCSuperclassMetaData();

		getFacade(parentMeta, serializer);

		return facade;

	}

	/**
	 * Get the class name from the discriminator string. Null if one doesn't
	 * exist
	 * 
	 * @param discriminator
	 * @return
	 */
	public CassandraClassMetaData getClassFromDiscriminator(String discriminator) {
		return discriminators.get(discriminator);
	}

}
