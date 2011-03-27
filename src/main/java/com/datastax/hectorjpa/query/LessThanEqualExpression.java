/**
 * 
 */
package com.datastax.hectorjpa.query;

import org.apache.openjpa.kernel.exps.ExpressionVisitor;
import org.apache.openjpa.kernel.exps.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Todd Nine
 *
 */
public class LessThanEqualExpression extends EqualityExpression {

 

  private static final Logger log = LoggerFactory.getLogger(LessThanEqualExpression.class);
  
  
  /**
   * 
   */
  private static final long serialVersionUID = -8763196039567306596L;

  public LessThanEqualExpression(Value parameter, Value value) {
    super(parameter, value);
  }
  
  @Override
  public void acceptVisit(ExpressionVisitor visitor) {
    // TODO Auto-generated method stub
    
  }

}
