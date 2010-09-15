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
package org.jboss.beach.metadata.generator;

import org.jboss.beach.metadata.generator.xsd.Annotated;
import org.jboss.beach.metadata.generator.xsd.Attribute;
import org.jboss.beach.metadata.generator.xsd.ComplexType;
import org.jboss.beach.metadata.generator.xsd.Documentation;
import org.jboss.beach.metadata.generator.xsd.Element;
import org.jboss.beach.metadata.generator.xsd.ExplicitGroup;
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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBElement;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.namespace.QName;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class Generator
{
   private static class Property
   {
      final String name;
      final String comment;
      final String type;

      Property(String name, String comment, String type)
      {
         assert name != null : "name is null";

         this.name = name;
         this.comment = comment;
         this.type = type;
      }
   }

   private String packageName;
   private File destDir;
   private File packageDir;
   private Unmarshaller unmarshaller;

   private List<Schema> generateSchemas = new ArrayList<Schema>();
   private Map<String, Schema> knownSchemas = new HashMap<String, Schema>();
   private Map<Schema, String> knownSchemaPackages = new HashMap<Schema, String>();
   
   public Generator(String packageName, File destDir) throws JAXBException
   {
      this.packageName = packageName;
      this.destDir = destDir;

      String packageDirName = packageName.replace(".", File.separator);
      packageDir = new File(destDir, packageDirName);
      packageDir.mkdirs();
      
      JAXBContext jc = JAXBContext.newInstance("org.jboss.beach.metadata.generator.xsd");
      unmarshaller = jc.createUnmarshaller();
   }

   public void add(String xsdFileName) throws JAXBException
   {
      int i = xsdFileName.lastIndexOf(File.separator);
      String dirName = ".";
      String xsdBaseName = xsdFileName;
      if(i >= 0)
      {
         dirName = xsdFileName.substring(0, i);
         xsdBaseName = xsdFileName.substring(i + 1);
      }
      File dir = new File(dirName);
      File file = new File(dir, xsdBaseName);
      Schema schema = (Schema) unmarshaller.unmarshal(file);
      generateSchemas.add(schema);
      knownSchemas.put(xsdBaseName, schema);
      knownSchemaPackages.put(schema, packageName);
      for(OpenAttrs attrs : schema.getIncludeOrImportOrRedefine())
      {
         if(attrs instanceof Include)
         {
            Include include = (Include) attrs;
            String location = include.getSchemaLocation();
            if(!knownSchemas.containsKey(location))
               knownSchemas.put(location, (Schema) unmarshaller.unmarshal(new File(dir, location)));
//            knownSchemaPackages.put(schema, packageName);
         }
      }
   }

   private static String comma(List<String> list)
   {
      String s = "";
      for(int i = 0; i < list.size(); i++)
      {
         s += list.get(i);
         if(i < (list.size() - 1))
            s += ", ";
      }
      return s;
   }

   /**
    * Create nice Java comment.
    */
   private static String comment(String s)
   {
      return comment("", s);
   }

   private static String comment(String prepend, String s)
   {
      if(s == null)
         return null;

      String r = prepend + "/**\n";
      StringTokenizer st = new StringTokenizer(s, "\n");
      while(st.hasMoreTokens())
      {
         r += prepend + " * " + st.nextToken().trim() + "\n";
      }
      r += prepend + " */";
      return r;
   }

   private String determineJavaType(Schema schema, String name, SimpleContent content)
   {
      if(content.getExtension() != null)
      {
         if(content.getExtension().getAttributeOrAttributeGroup().size() > 0)
         {
            // javaee:xsdStringType only has an id, lets ignore that
            Attribute attr = (Attribute) content.getExtension().getAttributeOrAttributeGroup().get(0);
            if(attr.getName() != null && attr.getName().equals("id"))
               return determineJavaType(content.getExtension().getBase());
            return packageNamePrefix(schema) + javaIdentifier(name);
         }
         return determineJavaType(content.getExtension().getBase());
      }
      else
      {
         SimpleRestrictionType restriction = content.getRestriction();
         if(restriction.getFacets().size() > 0 && !(restriction.getFacets().get(0) instanceof Pattern))
            return packageNamePrefix(schema) + javaIdentifier(name);
         return determineJavaType(content.getRestriction().getBase());
      }
   }

   private String determineJavaType(QName name)
   {
      String namespaceURI = name.getNamespaceURI();
      if(namespaceURI.equals("http://www.w3.org/2001/XMLSchema"))
      {
         String localPart = name.getLocalPart();
         if(localPart.equals("boolean"))
            return "Boolean";
         if(localPart.equals("integer") || localPart.equals("nonNegativeInteger"))
            return "Integer";
         if(localPart.equals("string") || localPart.equals("token"))
            return "String";
         if(localPart.equals("anyURI"))
            return URI.class.getName();
         if(localPart.equals("QName"))
            return QName.class.getName();
         throw new IllegalArgumentException("Can't handle " + name);
      }
      // special cases
      if(namespaceURI.equals("http://java.sun.com/xml/ns/javaee"))
      {
         String localPart = name.getLocalPart();
         if(localPart.equals("generic-booleanType"))
            return "Boolean";
      }
      for(Schema schema : knownSchemas.values())
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
               return packageNamePrefix(schema) + javaIdentifier(group.getName());
            }
            else if(attrs instanceof ComplexType)
            {
               ComplexType type = (ComplexType) attrs;
               if(!type.getName().equals(name.getLocalPart()))
                  continue;
               if(type.getSimpleContent() != null)
               {
                  return determineJavaType(schema, type.getName(), type.getSimpleContent());
               }
               if(type.getSequence() != null)
               {
                  //generateInterface(schemas, type.getName(), type.getSequence());
                  return packageNamePrefix(schema)  + javaIdentifier(type.getName());
               }
               throw new RuntimeException("NYI " + name);
            }
            else if(attrs instanceof SimpleType)
            {
               SimpleType type = (SimpleType) attrs;
               if(!type.getName().equals(name.getLocalPart()))
                  continue;
               if(type.getRestriction() != null)
                  return determineJavaType(type.getRestriction().getBase());
               if(type.getList() != null)
                  return "java.util.List<" + determineJavaType(type.getList().getItemType()) + ">";
               // TODO: handle it properly
               return URI.class.getName();
            }
         }
      }
      throw new RuntimeException("NYI " + name);
   }

   private static String documentation(Annotated annotated)
   {
      if(annotated.getAnnotation() == null)
         return null;
      Documentation doc = (Documentation) annotated.getAnnotation().getAppinfoOrDocumentation().get(0);
      return (String) doc.getContent().get(0);
   }

   public void generate() throws IOException
   {
      for(Schema schema : generateSchemas)
      {
         for(OpenAttrs attrs : schema.getSimpleTypeOrComplexTypeOrGroup())
         {
            if(attrs instanceof NamedGroup)
            {
               NamedGroup group = (NamedGroup) attrs;
               generateInterface(group.getName(), documentation(group), (ExplicitGroup) ((JAXBElement) group.getParticle().get(0)).getValue());
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
               else if(type.getName().equals("generic-booleanType"))
               {
                  // TODO:
                  continue;
               }
               if(type.getSimpleContent() != null)
                  generateInterface(type.getName(), documentation(type), type.getSimpleContent());
               else
                  generateInterface(type.getName(), documentation(type), type.getSequence());
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
   
   private void generateInterface(String name, String documentation, Group group) throws IOException
   {
      assert group != null : "group is null on " + name;

      List<String> extensions = new ArrayList<String>();
      List<Property> properties = new ArrayList<Property>();

      for(Object p : group.getParticle())
      {
         Object value = ((JAXBElement) p).getValue();
         if(value instanceof ExplicitGroup)
         {
            ExplicitGroup g = (ExplicitGroup) value;
            for(Object p2 : g.getParticle())
            {
               Object v2 = ((JAXBElement) p2).getValue();
               //System.out.println("  " + v2);
               if(v2 instanceof Element)
               {
                  Element element = (Element) v2;
                  boolean isCollection = element.getMaxOccurs().equals("unbounded");
                  String type = determineJavaType(element.getType());
                  if(isCollection)
                     type = "java.util.List<" + type + ">";
                  properties.add(new Property(element.getName(), comment("   ", documentation(element)), type));
               }
               else if(v2 instanceof GroupRef)
               {
                  GroupRef ref = (GroupRef) v2;
                  String type = determineJavaType(ref.getRef());
                  //assert type.startsWith(packageName) : type + " does not start with " + packageName + " " + name;
                  extensions.add(type);
               }
               else
                  throw new IllegalStateException(v2.toString());
            }
         }
         else if(value instanceof GroupRef)
         {
            GroupRef ref = (GroupRef) value;
            String type = determineJavaType(ref.getRef());
            //assert type.startsWith(packageName) : type + " does not start with " + packageName + " " + name;
            extensions.add(type);
         }
         else if(value instanceof Element)
         {
            Element element = (Element) value;
            boolean isCollection = element.getMaxOccurs().equals("unbounded");
            String type = determineJavaType(element.getType());
            if(isCollection)
               type = "java.util.List<" + type + ">";
            properties.add(new Property(element.getName(), comment("   ", documentation(element)), type));
         }
         else
            throw new IllegalStateException(value.toString() + " on " + name);
      }

      String identifier = javaIdentifier(name);
      File source = new File(packageDir, identifier + ".java");
      PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(source)));

      out.println("package " + packageName + ";");
      out.println();
      if(documentation != null)
         out.println(comment(documentation));
      out.println("public interface " + javaIdentifier(name) + (extensions.size() > 0 ? " extends " + comma(extensions) : ""));
      out.println("{");
      for(Property property : properties)
      {
         String s = javaIdentifier(property.name);
         if(property.comment != null)
            out.println(property.comment);
         out.println("   " + property.type + " get" + s + "();");
         out.println("   void set" + s + "(" + property.type + " " + normalize(property.name) + ");");
         out.println();
      }
      out.println("}");

      out.flush();
      out.close();
      System.out.println("Created " + source);
   }

   private void generateInterface(String name, String documentation, SimpleContent content) throws IOException
   {
      // hmm, duplicates logic in determineJavaType

      if(content.getExtension() != null)
      {
         if(content.getExtension().getAttributeOrAttributeGroup().size() == 0)
            return;

         // javaee:xsdStringType only has an id, lets ignore that
         Attribute attr = (Attribute) content.getExtension().getAttributeOrAttributeGroup().get(0);
         if(attr.getName() != null && attr.getName().equals("id"))
            return;

         // TODO: it's probably descriptionType
         String identifier = javaIdentifier(name);
         File source = new File(packageDir, identifier + ".java");
         PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(source)));

         out.println("package " + packageName + ";");
         out.println();
         if(documentation != null)
            out.println(comment(documentation));
         out.println("public interface " + identifier);
         out.println("{");
         out.println("   String getValue();");
         out.println("   void setValue(String value);");
         for(Annotated element : content.getExtension().getAttributeOrAttributeGroup())
         {
            Attribute a = (Attribute) element;
            String n = a.getName();
            if(n == null)
               n = a.getRef().getLocalPart();
            String s = javaIdentifier(n);
            out.println("   String get" + s + "();");
            out.println("   void set" + s + "(String " + normalize(n) + ");");
         }
         out.println("}");

         out.flush();
         out.close();
         System.out.println("Created " + source);
         return;
      }

      SimpleRestrictionType restriction = content.getRestriction();
      if(restriction.getFacets().size() == 0)
         return;

      if(restriction.getFacets().get(0) instanceof Pattern)
         return;

      String identifier = javaIdentifier(name);
      File source = new File(packageDir, identifier + ".java");
      PrintStream out = new PrintStream(new BufferedOutputStream(new FileOutputStream(source)));

      out.println("package " + packageName + ";");
      out.println();
      if(documentation != null)
         out.println(comment(documentation));
      out.println("public enum " + identifier);
      out.println("{");
      for(Object element : restriction.getFacets())
      {
         Facet facet = (Facet) ((JAXBElement) element).getValue();
         out.println("   " + facet.getValue() + ",");
      }
      out.println("}");

      out.flush();
      out.close();
      System.out.println("Created " + source);
   }
   
   public static void generate(String destDirName, String destPkg, String... xsdFileNames) throws IOException
   {
      File destDir = new File(destDirName);
      destDir.mkdirs();

      try
      {
         Generator generator = new Generator(destPkg, destDir);
         for(String s : xsdFileNames)
            generator.add(s);
         generator.generate();
      }
      catch(JAXBException e)
      {
         throw new IOException(e);
      }
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

   private String packageNamePrefix(Schema schema)
   {
      String pkg = knownSchemaPackages.get(schema);
      if(pkg == null)
         throw new IllegalStateException("No package name known for schema " + schema);
      if(pkg.equals(packageName))
         return "";
      return pkg + ".";
   }
}
