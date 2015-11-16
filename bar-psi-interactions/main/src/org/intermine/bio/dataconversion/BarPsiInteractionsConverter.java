package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2015 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import org.apache.commons.collections.keyvalue.MultiKey;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.tools.ant.BuildException;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.util.FormattedTextParser;
import org.intermine.xml.full.Item;


/**
 *
 * @author  sc
 */
public class BarPsiInteractionsConverter extends BioFileConverter
/*
{
    //
    private static final String DATASET_TITLE = "Add DataSet.title here";
    private static final String DATA_SOURCE_NAME = "Add DataSource.name here";

    public BarPsiInteractionsConverter(ItemWriter writer, Model model) {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
    }

    public void process(Reader reader) throws Exception {
    }
}
*/

{
    private static final Logger LOG = Logger.getLogger(BarPsiInteractionsConverter.class);

    private static final String DATASET_TITLE = "BAR interactions";
    private static final String DATA_SOURCE_NAME = "BAR";

    // for the moment dealing only with ath
    private Item org;
    private static final String ATH_TAXID = "3702";

    private static final String UNIPROT = "uniprotkb:";
    private static final String TAIR = "tair:";
    private static final String PSI = "psi-mi:";
    private static final String PUBMED = "pubmed:";
    private static final String TAXID = "taxid:";

    private Map<String, String> pubItems = new HashMap<String, String>();
    private Map<String, String> geneItems = new HashMap<String, String>();
    private Map<String, String> MIcodes = new HashMap<String, String>();

    private static final String PROP_FILE = "psi-intact_config.properties";
    private Map<String, String> pubs = new HashMap<String, String>();
    private Map<String, Object> experimentNames = new HashMap<String, Object>();
    private Map<String, String> terms = new HashMap<String, String>();
    private Map<String, String> regions = new HashMap<String, String>();
    private String termId = null;
//    private static final String INTERACTION_TYPE = "physical";
    private Map<String, String[]> config = new HashMap<String, String[]>();
//    private Set<String> taxonIds = null;
    private Map<String, String> genes = new HashMap<String, String>();
    private Map<MultiKey, Item> interactions = new HashMap<MultiKey, Item>();
    private String ALIAS_TYPE = "gene name";
    private static final String SPOKE_MODEL = "prey";   // don't store if all roles prey
    private static final String DEFAULT_IDENTIFIER = "symbol";
    private static final String DEFAULT_DATASOURCE = "";
//    private static final String BINDING_SITE = "MI:0117";
//    private static final Set<String> INTERESTING_COMMENTS = new HashSet<String>();
//    private static final String ATH_TAXONID = "3702";

//    protected IdResolver rslv;



    /**
     * Constructor
     * @param writer the ItemWriter used to handle the resultant items
     * @param model the Model
     */
    public BarPsiInteractionsConverter(ItemWriter writer, Model model)
        throws ObjectStoreException {
        super(writer, model, DATA_SOURCE_NAME, DATASET_TITLE);
        createOrganismItem();
    }

    /**
     *
     *
     * {@inheritDoc}
     */
    public void process(Reader reader) throws Exception {
        File currentFile = getCurrentFile();

        if ("mitabOut.txt".equals(currentFile.getName())) {
            processFile(reader, org);
        } else {
            LOG.warn("WWSS skipping file: " + currentFile.getName());
            //            throw new IllegalArgumentException("Unexpected file: "
            //          + currentFile.getName());
        }
    }

    /**
     * Process all rows of the mitab file, available at
     * ****
     *
     * @param reader
     *            a reader for the mitab file
     *
     * @throws IOException
     * @throws ObjectStoreException
     *
     * FILE FORMAT tsv
     * NO HEADER
     *
     * InteractorA id!InteractorB id!A 2id!B 2id!A Aliases!B Aliases!
     * Interaction detection methods!First author!pubmedid!A taxid!B taxid!
     * Interaction types!Source databases!Interaction identifier(s)!Confidence score
     *
     *
     * EXAMPLE
     *
     * tair:At4g23810!tair:At2g41090!uniprotkb:Q9SUP6!uniprotkb:P30187!tair:ATWRKY53|tair:WRKY53!-!
     * -!-!pubmed:17360592!taxid:3702!taxid:3702!
     * psi-mi:"MI:2165"(BAR)!-!-!
     *
     *
     */
    private void processFile(Reader reader, Item organism)
            throws IOException, ObjectStoreException {
        Iterator<?> tsvIter;
        try {
            tsvIter = FormattedTextParser.parseTabDelimitedReader(reader);
        } catch (Exception e) {
            throw new BuildException("cannot parse file: " + getCurrentFile(), e);
        }
        IdResolver athResolver = IdResolverService.getIdResolverByOrganism("3702");
        String pidA = null;

        String [] headers = null;
        int lineNumber = 0;

        while (tsvIter.hasNext()) {
            String[] line = (String[]) tsvIter.next();

//            // this can be omitted
//            if (lineNumber == 0) {
//                checkHeader(line);
//                lineNumber++;
//                continue;
//            }


            String geneIdA = parseToken(line [0]);
            String geneIdB = parseToken(line [1]);
            String protIdA = parseToken(line [2]);
            String protIdB = parseToken(line [3]);
            String geneSynA = parseToken(line [4]);
            String geneSynB = parseToken(line [5]);
            String detectionMethod = parseToken(line [6]);
            String FirstAuthor = line [7];
            String pubMedId = parseToken(line [8]);
            String taxidA = parseToken(line[9]);
            String taxidB = parseToken(line[10]);
            String type = parseToken(line[11]);
            String db = parseToken(line [12]);
            String ids = line [13];
            String score = line [14];

            // dealing only with ATH for now
            if (!taxidA.equalsIgnoreCase(ATH_TAXID)) {
                continue;
            }

//            int resCount = athResolver.countResolutions(taxidA, geneIdA);
//            if (resCount != 1) {
//                LOG.info("RESOLVER: failed to resolve gene to one identifier, ignoring gene: "
//                        + geneIdA + " count: " + resCount);
//                continue;
//            }


            // NOT WORKING!? see
            // http://intermine.readthedocs.org/en/latest/database/data-sources/id-resolvers
            //          if (pid == null) {
            //              LOG.info("MISSING ID: " + geneId);
            //              continue;
            //          }


//            pidA = athResolver.resolveId(taxidA, geneIdA).iterator().next();
            pidA = geneIdA;

//            LOG.info("READING " + pidA + "<->" + geneIdA + "|" + pubMedId + "|"
//                    + protIdA + "--" + type + "|" + detectionMethod + "|" + db);

//            createBioEntity(pidA, "Gene");

            String refIdA = createBioEntity(pidA, "Gene");
            String refIdB = createBioEntity(geneIdB,"Gene");

            Item interaction = getInteraction(refIdA, refIdB);
            Item interactionDetail =  createItem("InteractionDetail");

            if (StringUtils.isNotBlank(pubMedId)) {
                Item exp = createPublication(pubMedId, interactionDetail);
//                interactionDetail.setReference("experiment", exp);
            }


            //            String shortName = h.shortName;
//            interactionDetail.setAttribute("name", shortName);
//            interactionDetail.setAttribute("role1", role1);
//            interactionDetail.setAttribute("role2", role2);
            interactionDetail.setAttribute("type", type);
            if (StringUtils.isNumeric(score)) {
                interactionDetail.setAttribute("confidence", score);
            }
//                interactionDetail.setAttribute("relationshipType", h.relationshipType);
//            interactionDetail.setReference("experiment", experiment.getIdentifier());
            interactionDetail.setReference("interaction", interaction);
//            processRegions(h, interactionDetail, gene1Interactor, shortName, gene1RefId);
//            interactionDetail.addCollection(allInteractors);
            store(interactionDetail);


            lineNumber++;
        }
    }

    private Item getInteraction(String refId, String gene2RefId) throws ObjectStoreException {
        MultiKey key = new MultiKey(refId, gene2RefId);
        Item interaction = interactions.get(key);
        if (interaction == null) {
            interaction = createItem("Interaction");
            interaction.setReference("participant1", refId);
            interaction.setReference("participant2", gene2RefId);
            interactions.put(key, interaction);
            store(interaction);
        }
        return interaction;
    }




    // just get first element for now
    private String parseToken(String value) {
        String token;
        if (value.contains("|")) {
            token = value.split("|")[0];
        } else {
            token = value;
        }
        // 0,1,4,5
        if (token.startsWith(TAIR)) {
            return token.replace(TAIR, "").toUpperCase();
            //return StringUtils.remove(token, "tair:");
        }
        // 2,3
        if (token.startsWith(UNIPROT)) {
            return token.replace(UNIPROT, "");
        }
        // 6,11,12
        if (token.startsWith(PSI)) {
            // fill map with definitions
            String miCode = token.replace(PSI, "").substring(1, 8);
            if (!MIcodes.containsKey(miCode)) {
//                String description = StringUtils.substringAfterLast(token, "(").replace(')','');
                String description = StringUtils.substringAfterLast(token, "(");
                        //.replaceFirst(")","");
                MIcodes.put(miCode, description);
                LOG.info("MI CODES:" + miCode + "|" + description);
            }
            return token.replace(PSI, "").substring(1, 8);
        }
        // 8
        if (token.startsWith(PUBMED)) {
            return token.replace(PUBMED, "");
        }
        // 9,10
        if (token.startsWith(TAXID)) {
            return token.replace(TAXID, "");
        }
        return token;
    }

    /**
     * @param line
     */
    private void checkHeader(String[] line) {
        // column headers - strip off any extra columns - FlyAtlas
        // not necessary
        String[] headers;
        int end = 0;
        for (int i = 0; i < line.length; i++) {
            // if (StringUtils.isEmpty(line[i])) {
            if (line[i].isEmpty()) {
                break;
            }
            end++;
        }
        headers = new String[end];
        System.arraycopy(line, 0, headers, 0, end);
        LOG.info("WW header lenght " + headers.length);
    }


    /**
     * Create and store a GeneRIF item on the first time called.
     *
     * @param annotation the RIF note
     * @param timeStamp
     * @return an Item representing the geneRIF
     */
    private Item createGeneRIF(String annotation, String timeStamp) throws ObjectStoreException {
        Item generif = createItem("Generif");
        generif.setAttribute("annotation", annotation);
        generif.setAttribute("timeStamp", timeStamp);

        return generif;
    }


    /**
     * Create and store a BioEntity item on the first time called.
     *
     * @param primaryId the primaryIdentifier
     * @param type the type of bioentity (gene, exon..)
     * @throws ObjectStoreException
     */
    private String createBioEntity(String primaryId, String type) throws ObjectStoreException {
        // doing only genes here
        Item bioentity = null;

        if ("Gene".equals(type)) {
            if (!geneItems.containsKey(primaryId)) {
                bioentity = createItem("Gene");
                bioentity.setAttribute("primaryIdentifier", primaryId);
                store(bioentity);
                geneItems.put(primaryId, bioentity.getIdentifier());
            }
        }
        return geneItems.get(primaryId);
    }
    /**
     * Create and store a Publication item on the first time called.
     *
     * @param primaryId the primaryIdentifier
     * @param type gene or exon
     * @throws ObjectStoreException
     */
    private Item createPublication(String primaryId, Item interaction) throws ObjectStoreException {
        Item pub = null;
        Item exp = null;
        if (!pubItems.containsKey(primaryId)) {
            pub = createItem("Publication");
            pub.setAttribute("pubMedId", primaryId);
            store(pub);
            pubItems.put(primaryId, pub.getIdentifier());
            exp = createItem("InteractionExperiment");
            exp.setAttribute("name", "Exp-" + primaryId);
            exp.setReference("publication", pub);
            interaction.setReference("experiment", exp);
//            exp.addToCollection("interactions", interactionDetail);
            store(exp);
        }
        return exp;
    }


    /**
     * Create and store a organism item on the first time called.
     *
     * @throws ObjectStoreException os
     */
    protected void createOrganismItem() throws ObjectStoreException {
        org = createItem("Organism");
        org.setAttribute("taxonId", ATH_TAXID);
        store(org);
    }

}

