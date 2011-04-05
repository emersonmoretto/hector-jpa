/**
 * 
 */
package com.datastax.hectorjpa.meta;

import static com.datastax.hectorjpa.serializer.CompositeUtils.newComposite;

import java.util.Set;

import me.prettyprint.cassandra.model.HColumnImpl;
import me.prettyprint.hector.api.Keyspace;
import me.prettyprint.hector.api.beans.DynamicComposite;
import me.prettyprint.hector.api.mutation.Mutator;

import org.apache.openjpa.kernel.OpenJPAStateManager;

import com.datastax.hectorjpa.index.IndexDefinition;
import com.datastax.hectorjpa.query.FieldExpression;
import com.datastax.hectorjpa.query.IndexQuery;
import com.datastax.hectorjpa.store.CassandraClassMetaData;

/**
 * Class to perform all operations for secondary indexing on an instance in the
 * statemanager
 * 
 * @author Todd Nine
 * 
 */
public class IndexOperation extends AbstractIndexOperation {

  public IndexOperation(CassandraClassMetaData metaData,
      IndexDefinition indexDef) {
    super(metaData, indexDef);
  }

  /**
   * Write the index definition
   * 
   * @param stateManager
   *          The objects state manager
   * @param mutator
   *          The mutator to write to
   * @param clock
   *          the clock value to use
   */
  public void writeIndex(OpenJPAStateManager stateManager,
      Mutator<byte[]> mutator, long clock) {

    DynamicComposite newComposite = null;
    DynamicComposite oldComposite = null;
    DynamicComposite tombstoneComposite = null;

    // loop through all added objects and create the writes for them.
    // create our composite of the format of id+order*
    newComposite = newComposite();

    // create our composite of the format order*+id
    oldComposite = newComposite();
    
    
    // create our composite of the format id+order*
    tombstoneComposite = newComposite();

    boolean changed = constructComposites(newComposite, oldComposite, tombstoneComposite,
        stateManager);

    mutator.addInsertion(indexName, CF_NAME,
        new HColumnImpl<DynamicComposite, byte[]>(newComposite, HOLDER, clock,
            compositeSerializer, bytesSerializer));
    
    mutator.addInsertion(reverseIndexName, CF_NAME,
            new HColumnImpl<DynamicComposite, byte[]>(tombstoneComposite, HOLDER, clock,
                compositeSerializer, bytesSerializer));

    // value has changed since we loaded. Remove the old value
    if (changed) {

      // add it to our old value
      mutator.addDeletion(indexName, CF_NAME, oldComposite,
          compositeSerializer, clock);

    }
  }

  /**
   * Scan the given index query and add the results to the provided set. The set
   * comparator of the dynamic columns are compared via a tree comparator
   * 
   * @param query
   */
  public void scanIndex(IndexQuery query, Set<DynamicComposite> results,
      Keyspace keyspace) {

    DynamicComposite startScan = newComposite();
    DynamicComposite endScan = newComposite();
    
    int length = fields.length;

    for (int i = 0; i < length; i++) {

      FieldExpression exp = query.getExpression(this.fields[i].getMetaData());

      this.fields[i].addToComposite(startScan, i, exp.getStart(),
          getEquality(exp.getStartEquality(), i, length));
      this.fields[i].addToComposite(endScan, i, exp.getEnd(),
          getEquality(exp.getEndEquality(), i, length));
    }

    super.executeQuery(startScan, endScan, results, keyspace);

  }

}
