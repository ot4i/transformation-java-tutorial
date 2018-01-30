/* 
 * Sample program for use with Product          
 *  ProgIds: 5724-J06 5724-J05 5724-J04 5697-J09 5655-M74 5655-M75 5648-C63 
 *  (C) Copyright IBM Corporation 2005.                      
 * All Rights Reserved * Licensed Materials - Property of IBM 
 * 
 * This sample program is provided AS IS and may be used, executed, 
 * copied and modified without royalty payment by customer 
 * 
 * (a) for its own instruction and study, 
 * (b) in order to develop applications designed to run with an IBM 
 *     WebSphere product, either for customer's own internal use or for 
 *     redistribution by customer, as part of such an application, in 
 *     customer's own products. 
 */

import com.ibm.broker.plugin.*;
import com.ibm.broker.javacompute.MbJavaComputeNode;

public class Transformation_JavaCompute extends MbJavaComputeNode
{
	private String curency;
  public void evaluate(MbMessageAssembly inAssembly)
    throws MbException
  {
    MbMessage inMessage = inAssembly.getMessage();
    MbMessage outMessage = new MbMessage();  // create an empty output message
    MbMessageAssembly outAssembly = new MbMessageAssembly(inAssembly, outMessage);

    copyMessageHeaders(inMessage, outMessage); // copy headers from the input message

    // Add user code below

    MbElement inRoot = inMessage.getRootElement();
    MbElement outRoot = outMessage.getRootElement();
    MbElement outBody = outRoot.createElementAsLastChild("XMLNSC");  // create the 'Body' XMLNSC element

    // set up the iterative operations using ForEachChildOperation helper class

    // declare the class to create articles for each invoice
    final ForEachChildOperation createArticle = new ForEachChildOperation("Item") {
        private double total;

        protected void before()
        {
          total = 0;
        }

        protected void forEachElement(MbElement item) throws MbException
        {
          MbElement purchases = getOutputElement(); // build the output tree from this point
          MbElement article = purchases.createElementAsLastChild(MbXMLNSC.FOLDER, "Article", null);
          MbElement cursor = item.getFirstElementByPath("Description");
          article.createElementAsLastChild(MbXMLNSC.FIELD,
                                           "Desc",
                                           (String)cursor.getValue());

          cursor = cursor.getNextSibling().getNextSibling(); // Price element
          double cost = Double.parseDouble((String)cursor.getValue()) * 1.6;
          cost = (int) (cost*1000)/1000.0; // rounding
          cursor = cursor.getNextSibling(); // Quantity element;        
          
          String quantity = (String)cursor.getValue();
          int quantity_int = Integer.parseInt(quantity);
          total += cost * quantity_int;
          
          article.createElementAsLastChild(MbXMLNSC.FIELD, "Cost", Double.toString(cost));
      	  article.createElementAsLastChild(MbXMLNSC.FIELD, "Qty", quantity);
        }

        protected void after() throws MbException
        {
          total = (int) (total*1000)/1000.0; // rounding
          getOutputElement().createElementAfter(MbXMLNSC.FIELD, "Amount", Double.toString(total)).createElementAsFirstChild(MbXMLNSC.ATTRIBUTE, "Currency", curency);
        }
      };

    // declare the class to create statements for each salelist
    final ForEachChildOperation createStatement = new ForEachChildOperation("Invoice") {
        protected void forEachElement(MbElement invoice) throws MbException
        {
          MbElement outSaleList = getOutputElement();

          MbElement cursor = invoice.getFirstChild();
          String initial1 = (String)cursor.getValue();
          cursor = cursor.getNextSibling();
          String initial2 = (String)cursor.getValue();
          String initials = initial1 + initial2;

          cursor = cursor.getNextSibling();
          String surname = (String)cursor.getValue();

          String balance = (String)invoice.getFirstElementByPath("Balance").getValue();
          curency = (String)invoice.getFirstElementByPath("Currency").getValue();

          MbElement statement = outSaleList.createElementAsLastChild(MbXMLNSC.FOLDER, "Statement", null);
          statement.createElementAsLastChild(MbXMLNSC.ATTRIBUTE, "Type", "Monthly");
          statement.createElementAsLastChild(MbXMLNSC.ATTRIBUTE, "Style", "Full");

          MbElement customer = statement.createElementAsLastChild(MbXMLNSC.FOLDER, "Customer", null);
          customer.createElementAsLastChild(MbXMLNSC.FIELD, "Initials", initials);
          customer.createElementAsLastChild(MbXMLNSC.FIELD, "Name", surname);
          customer.createElementAsLastChild(MbXMLNSC.FIELD, "Balance", balance);

          MbElement purchases = statement.createElementAsLastChild(MbXMLNSC.FOLDER, "Purchases", null);

          // Now create the articles for each invoice
          createArticle.setOutputElement(purchases);
          createArticle.evaluate(invoice);
        }
      };

    // declare the class to create the output salelist for each input salelist
    final ForEachChildOperation createSaleList = new ForEachChildOperation("SaleList") {
        protected void forEachElement(MbElement saleList) throws MbException
        {

          MbElement outSaleList = getOutputElement().createElementAsLastChild(MbXMLNSC.FOLDER, "SaleList", null);

          // create the statements for each invoice
          createStatement.setOutputElement(outSaleList);
          createStatement.evaluate(saleList);
        }
      };

    // Now do the message transformation

    MbElement outSaleEnvelope = outBody.createElementAsFirstChild(MbXMLNSC.FOLDER, "SaleEnvelope", null);
    
    createSaleList.setOutputElement(outSaleEnvelope);
    createSaleList.evaluate(inRoot.getLastChild().getLastChild());

    // End of user code
    // The following should only be changed
    // if not propagating message to the 'out' terminal

    getOutputTerminal("out").propagate(outAssembly);
  }

  public void copyMessageHeaders(MbMessage inMessage, MbMessage outMessage) throws MbException
  {
    MbElement outRoot = outMessage.getRootElement();
    MbElement header = inMessage.getRootElement().getFirstChild();

    while(header != null && header.getNextSibling() != null)
      {
        outRoot.addAsLastChild(header.copy());
        header = header.getNextSibling();
      }
  }

  /**
   * ForEachChildOperation is an abstract helper class allowing a method to be repeatedly applied to
   * named children of an element.  The user can optionally access the output message tree
   * allowing message transformations to be defined based on repeating elements in the input
   * tree.
   */
  abstract class ForEachChildOperation
  {
    private String name = null;
    private MbElement outputElement = null;

    /**
     * Constructor taking the name of the repeating child.
     */
    public ForEachChildOperation(String name) throws MbException
    {
      this.name = name;
    }

    /**
     * Must be called prior to the evaluate method if the user wishes to work with the output
     * tree (eg for message transformation).
     */
    public void setOutputElement(MbElement out)
    {
      outputElement = out;
    }

    /**
     * Allows the user to access the output tree in the forEachElement() method
     */
    public MbElement getOutputElement()
    {
      return outputElement;
    }

    /**
     * Can be overridden by the user to do initialisation processing before iterating
     * over the children
     */
    protected void before() throws MbException { }

    /**
     * This gets called once for each named child element. The current element
     * is passed in as the only argument.  This method is abstract and must be implemented
     * in the derived class.
     */
    protected abstract void forEachElement(MbElement element) throws MbException;

    /**
     * Can be overridden by the user to do post-processing after iterating over the
     * children
     */
    protected void after() throws MbException { }

    /**
     * Called by the user to iterate over the XPath nodeset.
     *
     * @param element The context element to which the XPath expression will be applied.
     */
    public void evaluate(MbElement element) throws MbException
    {
      before();

      MbElement child = element.getFirstChild();
      while(child != null)
        {
          String childName = (String)child.getName();
          if(childName.equals(name))
            forEachElement(child);
          child = child.getNextSibling();
        }

      after();
    }

  }

}
