package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2008 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.HashMap;

import org.intermine.dataconversion.ItemsTestCase;
import org.intermine.dataconversion.MockItemWriter;
import org.intermine.metadata.Model;
import org.intermine.model.fulldata.Item;

public class BioGridHumanConverterTest extends ItemsTestCase
{
    public BioGridHumanConverterTest(String arg) {
        super(arg);
        // TODO Auto-generated constructor stub
    }

    public void setUp() throws Exception {
        super.setUp();
    }

    public void testProcess() throws Exception {

        Reader reader = 
            new InputStreamReader(getClass().getClassLoader().getResourceAsStream("test.tab"));

        MockItemWriter itemWriter = new MockItemWriter(new HashMap<String, Item>());
        BioGridHumanConverter converter = new BioGridHumanConverter(itemWriter,
                                                          Model.getInstanceByName("genomic"));

        converter.process(reader);
        converter.close();

        // uncomment to write out a new target items file
        writeItemsFile(itemWriter.getItems(), "BioGridHuman_test.xml");

        //Set expected = readItemSet("UniprotConverterTest_tgt.xml");

        //assertEquals(expected, itemWriter.getItems());
    }
}
