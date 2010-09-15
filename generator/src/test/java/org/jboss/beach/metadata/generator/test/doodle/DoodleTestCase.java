/*
 * JBoss, Home of Professional Open Source
 * Copyright (c) 2010, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @authors tag. See the copyright.txt in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.jboss.beach.metadata.generator.test.doodle;

import org.jboss.beach.metadata.generator.xsd.Attribute;
import org.jboss.beach.metadata.generator.xsd.ComplexType;
import org.jboss.beach.metadata.generator.xsd.Element;
import org.jboss.beach.metadata.generator.xsd.Facet;
import org.jboss.beach.metadata.generator.xsd.Group;
import org.jboss.beach.metadata.generator.xsd.GroupRef;
import org.jboss.beach.metadata.generator.xsd.Include;
import org.jboss.beach.metadata.generator.xsd.NamedGroup;
import org.jboss.beach.metadata.generator.xsd.OpenAttrs;
import org.jboss.beach.metadata.generator.xsd.Pattern;
import org.jboss.beach.metadata.generator.xsd.Schema;
import org.jboss.beach.metadata.generator.xsd.SimpleContent;
import org.jboss.beach.metadata.generator.xsd.SimpleRestrictionType;
import org.jboss.beach.metadata.generator.xsd.SimpleType;
import org.jboss.beach.metadata.generator.xsd.TopLevelComplexType;
import org.jboss.beach.metadata.generator.xsd.TopLevelSimpleType;
import org.junit.Test;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Free-form coding.
 *
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class DoodleTestCase
{
   private static final String packageName = "test";

   private static class Property
   {
      final String name;
      final String type;
      
      Property(String name, String type)
      {
         assert name != null : "name is null";

         this.name = name;
         this.type = type;
      }
   }

   private static String determineJavaType(List<Schema> schemas, String name, SimpleContent content)
   {
      if(content.getExtension() != null)
      {
         if(content.getExtension().getAttributeOrAttributeGroup().size() > 0)
         {
            // javaee:xsdStringType only has an id, lets ignore that
            Attribute attr = (Attribute) content.getExtension().getAttributeOrAttributeGroup().get(0);
            if(attr.getName() != null && attr.getName().equals("id"))
               return determineJavaType(schemas, content.getExtension().getBase());
            return packageName + "." + javaIdentifier(name);
         }
         return determineJavaType(schemas, content.getExtension().getBase());
      }
      else
      {
         SimpleRestrictionType restriction = content.getRestriction();
         if(restriction.getFacets().size() > 0 && !(restriction.getFacets().get(0) instanceof Pattern))
            return packageName + "." + javaIdentifier(name);
         return determineJavaType(schemas, content.getRestriction().getBase());
      }
   }

   private static String determineJavaType(List<Schema> schemas, QName name)
   {
      String namespaceURI = name.getNamespaceURI();
      if(namespaceURI.equals("http://www.w3.org/2001/XMLSchema"))
      {
         String localPart = name.getLocalPart();
         if(localPart.equals("boolean"))
            return "Boolean";
         if(localPart.equals("integer"))
            return "Integer";
         if(localPart.equals("string") || localPart.equals("token"))
            return "String";
         throw new IllegalArgumentException("Can't handle " + name);
      }
      else
      for(Schema schema : schemas)
      {
         if(!namespaceURI.equals(schema.getTargetNamespace()))
         {
            continue;
         }
         for(OpenAttrs attrs : schema.getSimpleTypeOrComplexTypeOrGroup())
         {
            if(attrs instanceof NamedGroup)
            {
               //throw new RuntimeException("NYI");
               NamedGroup group = (NamedGroup) attrs;
               if(!group.getName().equals(name.getLocalPart()))
                  continue;
               return packageName + "." + javaIdentifier(group.getName());
            }
            else if(attrs instanceof ComplexType)
            {
               ComplexType type = (ComplexType) attrs;
               if(!type.getName().equals(name.getLocalPart()))
                  continue;
               if(type.getSimpleContent() != null)
               {
                  return determineJavaType(schemas, type.getName(), type.getSimpleContent());
               }
               if(type.getSequence() != null)
               {
                  //generateInterface(schemas, type.getName(), type.getSequence());
                  return packageName + "." + javaIdentifier(type.getName());
               }
               throw new RuntimeException("NYI " + name);
            }
            else if(attrs instanceof SimpleType)
            {
               SimpleType type = (SimpleType) attrs;
               if(!type.getName().equals(name.getLocalPart()))
                  continue;
               return determineJavaType(schemas, type.getRestriction().getBase());
            }
         }
      }
      throw new RuntimeException("NYI " + name);
   }

   private static void generateInterface(List<Schema> schemas, String name, Group group)
   {
      assert group != null : "group is null on " + name;
      
      List<String> extensions = new ArrayList<String>();
      List<Property> properties = new ArrayList<Property>();

      for(Object p : group.getParticle())
      {
         Object value = ((JAXBElement) p).getValue();
         if(value instanceof Group)
         {
            Group g = (Group) value;
            //System.out.println(g.getParticle());
            for(Object p2 : g.getParticle())
            {
               Object v2 = ((JAXBElement) p2).getValue();
               //System.out.println("  " + v2);
               if(v2 instanceof Element)
               {
                  Element element = (Element) v2;
                  boolean isCollection = element.getMaxOccurs().equals("unbounded");
                  String type = determineJavaType(schemas, element.getType());
                  if(isCollection)
                     type = "List<" + type + ">";
                  properties.add(new Property(element.getName(), type));
               }
               else if(v2 instanceof GroupRef)
               {
                  GroupRef ref = (GroupRef) v2;
                  String type = determineJavaType(schemas, ref.getRef());
                  assert type.startsWith(packageName) : type + " does not start with " + packageName + " " + name;
                  extensions.add(type);
               }
               else
                  throw new IllegalStateException(v2.toString());
            }
         }
         else if(value instanceof Element)
         {
            Element element = (Element) value;
            boolean isCollection = element.getMaxOccurs().equals("unbounded");
            String type = determineJavaType(schemas, element.getType());
            if(isCollection)
               type = "List<" + type + ">";
            properties.add(new Property(element.getName(), type));
         }
         else
            throw new IllegalStateException(value.toString());
      }
      //System.out.println("package test;");
      System.out.println("public interface " + javaIdentifier(name) + (extensions.size() > 0 ? " extends " + extensions : ""));
      System.out.println("{");
      for(Property property : properties)
      {
         String identifier = javaIdentifier(property.name);
         System.out.println("   " + property.type + " get" + identifier + "();");
         System.out.println("   void set" + identifier + "(" + property.type + " " + normalize(property.name) + ");");
      }
      System.out.println("}");
   }

   private void generateInterface(List<Schema> schemas, String name, SimpleContent content)
   {
      // hmm, duplicates logic in determineJavaType

      if(content.getExtension() != null)
         return;

      SimpleRestrictionType restriction = content.getRestriction();
      if(restriction.getFacets().size() == 0)
         return;

      if(restriction.getFacets().get(0) instanceof Pattern)
         return;

      System.out.println("public enum " + javaIdentifier(name));
      System.out.println("{");
      for(Object element : restriction.getFacets())
      {
         Facet facet = (Facet) ((JAXBElement) element).getValue();
         System.out.println("   " + facet.getValue() + ",");
      }
      System.out.println("}");
   }

   private static String javaIdentifier(String s)
   {
      return normalize(Character.toUpperCase(s.charAt(0)) + s.substring(1));
   }

   private static String normalize(String s)
   {
      String result = "";
      StringTokenizer st = new StringTokenizer(s, "-");
      if(st.hasMoreTokens())
         result = st.nextToken();
      while(st.hasMoreTokens())
      {
         String token = st.nextToken();
         result += Character.toUpperCase(token.charAt(0)) + token.substring(1);
      }
      return result;
   }

   @Test
   public void test1() throws Exception
   {
      JAXBContext jc = JAXBContext.newInstance("org.jboss.beach.metadata.generator.xsd");
      Unmarshaller unmarshaller = jc.createUnmarshaller();
//      URL url = getClass().getResource("test1.xsd");
//      Schema schema = (Schema) unmarshaller.unmarshal(url);
      List<Schema> schemas = new ArrayList<Schema>();
      File dir = new File("../javaee/src/main/resources");
      File file = new File(dir, "javaee_6.xsd");
      Schema schema = (Schema) unmarshaller.unmarshal(file);
      schemas.add(schema);
      for(OpenAttrs attrs : schema.getIncludeOrImportOrRedefine())
      {
         if(attrs instanceof Include)
         {
            Include include = (Include) attrs;
            schemas.add((Schema) unmarshaller.unmarshal(new File(dir, include.getSchemaLocation())));
         }
      }
      //System.out.println(schemas);
      for(OpenAttrs attrs : schema.getSimpleTypeOrComplexTypeOrGroup())
      {
         if(attrs instanceof NamedGroup)
         {
            NamedGroup group = (NamedGroup) attrs;
            generateInterface(schemas, group.getName(), group);
         }
         else if(attrs instanceof TopLevelComplexType)
         {
            TopLevelComplexType type = (TopLevelComplexType) attrs;
            //System.out.println(type.getName());
            if(type.getName().equals("emptyType"))
            {
               // what did you expect?
               continue;
            }
            if(type.getSimpleContent() != null)
               generateInterface(schemas, type.getName(), type.getSimpleContent());
            else
               generateInterface(schemas, type.getName(), type.getSequence());
         }
         else if(attrs instanceof TopLevelSimpleType)
         {
            TopLevelSimpleType type = (TopLevelSimpleType) attrs;
            // TODO: ignore for now
            //throw new IllegalStateException(type.getName());
         }
         else
            throw new IllegalStateException("Can't handle " + attrs.getClass());
      }
   }
}
