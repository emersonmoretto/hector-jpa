/**
 * 
 */
package com.datastax.hectorjpa.meta.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.prettyprint.cassandra.model.HColumnImpl;
import me.prettyprint.cassandra.model.thrift.ThriftSliceQuery;
import me.prettyprint.cassandra.serializers.BytesArraySerializer;
import me.prettyprint.cassandra.serializers.StringSerializer;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.ColumnSlice;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.mutation.Mutator;
import me.prettyprint.hector.api.query.QueryResult;
import me.prettyprint.hector.api.query.SliceQuery;

import org.apache.openjpa.kernel.OpenJPAStateManager;
import org.apache.openjpa.kernel.StoreContext;
import org.apache.openjpa.meta.FieldMetaData;
import org.apache.openjpa.util.ChangeTracker;
import org.apache.openjpa.util.Proxy;

import com.datastax.hectorjpa.meta.collection.OrderedCollectionField.OrderField;
import com.datastax.hectorjpa.store.MappingUtils;

import compositecomparer.Composite;

/**
 * Represents an unordered collection
 * 
 * @author Todd Nine
 *
 */
public class UnorderedCollectionField<V> extends AbstractCollectionField<V> {

  //represents the end "id" in the key
  private static final byte[] unorderedMarker = StringSerializer.get().toBytes("u");
  
  public UnorderedCollectionField(FieldMetaData fmd, MappingUtils mappingUtils) {
    super(fmd, mappingUtils);
  }
  
  /*
   * (non-Javadoc)
   * 
   * @see com.datastax.hectorjpa.meta.collection.AbstractCollectionField#
   * getDefaultSearchmarker()
   */
  @Override
  protected byte[] getDefaultSearchmarker() {
    return unorderedMarker;
  }


  @Override
  public void readField(OpenJPAStateManager stateManager,
      QueryResult<ColumnSlice<Composite, byte[]>> result) {
    
    Object[] fields = null;

    StoreContext context = stateManager.getContext();

    // TODO TN use our CollectionProxy here
    Collection<Object> collection = (Collection<Object>) stateManager.newProxy(fieldId);
    
    
    for (HColumn<Composite, byte[]> col : result.get().getColumns()) {
      fields = col.getName().toArray();

      // the id will always be the first value in a composite type, we only care
      // about that value.
      Object nativeId = fields[0];

      collection.add(context.find(context.newObjectId(targetClass, nativeId),
          true, null));

    }

    // now load all the objects from the ids we were given.

    stateManager.storeObject(fieldId, collection);
    
  }

  @Override
  public void addField(OpenJPAStateManager stateManager,
      Mutator<byte[]> mutator, long clock, byte[] key, String cfName) {

   
    Object field = stateManager.fetch(fieldId);

    // nothing to do
    if (field == null) {
      return;
    }

    Set<HColumn<Composite, byte[]>> newIdColumns = new HashSet<HColumn<Composite, byte[]>>();
    Set<HColumn<Composite, byte[]>> deletedIdColumns = new HashSet<HColumn<Composite, byte[]>>();

    // not a proxy, it's the first time this has been saved
    if (field instanceof Proxy) {

      Proxy proxy = (Proxy) field;
      ChangeTracker changes = proxy.getChangeTracker();

      createColumns(stateManager, changes.getAdded(), newIdColumns, clock);

      // TODO TN need to get the original value to delete old index on change
      createColumns(stateManager, changes.getChanged(),  newIdColumns, clock);

      // add everything that needs removed
      createColumns(stateManager, changes.getRemoved(), deletedIdColumns, clock);
    }
    // new item that hasn't been proxied, just write them as new columns
    else {
      createColumns(stateManager, (Collection<?>) field,  newIdColumns, clock);
    }

    // construct the key
    byte[] idKey = constructKey(key, unorderedMarker);

   
    for (HColumn<Composite, byte[]> current : deletedIdColumns) {

      // same column exists on write, don't issue the delete since we don't want
      // to remove the write
      if (newIdColumns.contains(current)) {
        continue;
      }

      mutator.addDeletion(idKey, CF_NAME, current.getName(),
          compositeSerializer, clock);
    }

    // write our key updates
    for (HColumn<Composite, byte[]> current : newIdColumns) {

      mutator.addInsertion(idKey, CF_NAME, current);
    }

    
  }

  /**
   * Create columns and add them to the collection of columns.
   * 
   * @param stateManager
   * @param objects
   * @param orders
   * @param clock
   */
  private void createColumns(OpenJPAStateManager stateManager,
      Collection<?> objects, Set<HColumn<Composite, byte[]>> keys, long clock) {

    StoreContext ctx = stateManager.getContext();

    Composite idComposite = null;

    for (Object current : objects) {

      Object currentId = mappingUtils.getTargetObject(ctx.getObjectId(current));

      // create our composite of the format of id+order*
      idComposite = new Composite();
      
      // add our id to the beginning of our id based composite
      idComposite.add(currentId);

      // add our key based column to the key columns
      keys.add(new HColumnImpl<Composite, byte[]>(idComposite, HOLDER, clock,
          compositeSerializer, BytesArraySerializer.get()));

    }

  }

  
}