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

import org.jboss.beach.metadata.generator.Generator;
import org.junit.Test;

/**
 * @author <a href="mailto:cdewolf@redhat.com">Carlo de Wolf</a>
 */
public class GeneratorTestCase
{
   @Test
   public void testGenerate() throws Exception
   {
      //Generator.generate("javaee/src/main/resources/javaee_web_services_client_1_3.xsd", "generator/target/generated-sources/generator", "org.jboss.beach.metadata.generator.test.javaee");
      Generator.generate("target/generated-sources/generator", "org.jboss.beach.metadata.generator.test.javaee", "../javaee/src/main/resources/javaee_web_services_client_1_3.xsd", "../javaee/src/main/resources/javaee_6.xsd");
   }
}
