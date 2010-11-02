package org.intermine.bio.dataconversion;

/*
 * Copyright (C) 2002-2009 FlyMine
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence.  This should
 * be distributed with the code.  See the LICENSE file for more
 * information or http://www.gnu.org/copyleft/lesser.html.
 *
 */

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.intermine.dataconversion.ItemWriter;
import org.intermine.metadata.Model;
import org.intermine.objectstore.ObjectStoreException;
import org.intermine.sql.Database;
import org.intermine.xml.full.Item;

/**
 * 
 * @author
 */
public class EnsemblGwasDbConverter extends BioDBConverter
{
    // 
    private static final String DATASET_TITLE = "Ensembl GWAS data";
    private static final String DATA_SOURCE_NAME = "Ensembl";

    private Map<String, String> snps = new HashMap<String, String>();
    private Map<String, String> genes = new HashMap<String, String>();
    private Map<String, String> pubs = new HashMap<String, String>();
    private Map<String, String> sources = new HashMap<String, String>();
    private int taxonId = 9606;

    private static final Logger LOG = Logger.getLogger(EnsemblGwasDbConverter.class);

    /**
     * Construct a new EnsemblGwasDbConverter.
     * @param database the database to read from
     * @param model the Model used by the object store we will write to with the ItemWriter
     * @param writer an ItemWriter used to handle Items created
     */
    public EnsemblGwasDbConverter(Database database, Model model, ItemWriter writer) {
        super(database, model, writer, DATA_SOURCE_NAME, DATASET_TITLE);
    }


    /**
     * {@inheritDoc}
     */
    public void process() throws Exception {
        Connection connection = getDatabase().getConnection();

        int counter = 0;
        ResultSet res = queryVariationAnnotation(connection);
        while (res.next()) {
            String sourceName = res.getString("s.name");
            if ("HGMD-PUBLIC".equals(sourceName)) {
                continue;
            }

            counter++;

            Item result = createItem("GWASResult");
            setAttributeIfPresent(result, "phenotype", res.getString("p.description"));

            setAttributeIfPresent(result, "riskAlleleFreqInControls",
                    parseAlleleFreqDouble(res.getString("risk_allele_freq_in_controls")));
            setAttributeIfPresent(result, "pValue", parsePValueDouble(res.getString("p_value")));
            setAttributeIfPresent(result, "associatedVariantRiskAllele",
                    parseAllele(res.getString("associated_variant_risk_allele")));

            // SNP
            String rsNumber = res.getString("variation_name");
            String snpIdentifier = getSNPIdentifier(rsNumber);
            result.setReference("SNP", snpIdentifier);

            // PUBLICATION
            String study = res.getString("study");
            String pubIdentifier = getPubIdentifier(study);
            if (pubIdentifier != null) {
                result.setReference("publication", pubIdentifier);
            }
            
            // SOURCE
            String source = res.getString("s.name");
            result.setReference("source", getSourceIdentifier(source));

            // GENES
            String associatedGene = res.getString("associated_gene");
            List<String> geneCollection = getGeneCollection(associatedGene);
            if (!geneCollection.isEmpty()) {
                result.setCollection("associatedGenes", geneCollection);
            }

            store(result);
        }
    }

    private void setAttributeIfPresent(Item item, String attName, String attValue) {
        if (!StringUtils.isBlank(attValue)) {
            item.setAttribute(attName, attValue);
        }
    }

    private String parseAlleleFreqDouble(String input) {
        // clear up e.g. 0.03 (EA)
        if (!StringUtils.isBlank(input)) {
            if (input.indexOf('(') > 0) {
                input = input.substring(input.indexOf('(')).trim();
            }
            try {
                return "" + Double.parseDouble(input);
            } catch (NumberFormatException e) {
                LOG.warn("Could not parse allele frequency value: " + input);
                // no nothing, method will return null
            }
        }
        return null;
    }
    
    private String parsePValueDouble(String input) {
        // 10^-5 > p > 10^-6  -->  10E-5
        if (!StringUtils.isBlank(input)) {
            if (input.indexOf('>') > 0) {
                input = input.substring(0, input.indexOf('>')).trim();                
            }
            if (input.indexOf('^') > 0) {
                input = input.replace("^", "E");
            }
            try {
                return "" + Double.parseDouble(input);
            } catch (NumberFormatException e) {
                LOG.warn("Could not parse pValue: " + input);
                // no nothing, method will return null
            }
        }
        return null;
    }


    private String parseAllele(String input) {
        if (!StringUtils.isBlank(input)) {
            Pattern alleleMatcher = Pattern.compile("rs\\d*-(.)");
            Matcher m = alleleMatcher.matcher(input);
            if (m.find()) {
                String base = m.group(1);
                if ("?".equals(base)) {
                    return null;
                } else {
                    return m.group(1);
                }
            }
        }
        return input;
    }
    
    private String getSNPIdentifier(String rsNumber) throws ObjectStoreException {
        String snpIdentifier = snps.get(rsNumber);
        if (snpIdentifier == null) {
            Item snp = createItem("SNP");
            snp.setAttribute("primaryIdentifier", rsNumber);
            store(snp);
            snpIdentifier = snp.getIdentifier();
            snps.put(rsNumber, snpIdentifier);
        }
        return snpIdentifier;
    }

    private String getPubIdentifier(String study) throws ObjectStoreException {
        String pubmedIdentifier = null;
        if (!StringUtils.isBlank(study)) {
            pubmedIdentifier = pubs.get(study);
            if (pubmedIdentifier == null) {
                String pubmedId = study.substring("pubmed/".length());
                Item pub = createItem("Publication");
                pub.setAttribute("pubMedId", pubmedId);
                store(pub);
                pubmedIdentifier = pub.getIdentifier();
                pubs.put(study, pubmedIdentifier);
            }
        }
        return pubmedIdentifier;
    }

    private List<String> getGeneCollection(String input) throws ObjectStoreException {
        List<String> geneIdentifiers = new ArrayList<String>();
        if (!StringUtils.isBlank(input)) {
            for (String gene : input.split(",")) {
                geneIdentifiers.add(getGeneIdentifier(gene.trim().toUpperCase()));
            }
        }
        return geneIdentifiers;
    }

    private String getGeneIdentifier(String symbol) throws ObjectStoreException {
        String geneIdentifier = genes.get(symbol);
        if (geneIdentifier == null) {
            Item gene = createItem("Gene");
            if (symbol.startsWith("ENSG")) {
                gene.setAttribute("primaryIdentifier", symbol);
            } else {
                gene.setAttribute("symbol", symbol);
            }
            gene.setReference("organism", getOrganismItem(taxonId));
            store(gene);
            geneIdentifier = gene.getIdentifier();
            genes.put(symbol, geneIdentifier);
        }
        return geneIdentifier;
    }

    private String getSourceIdentifier(String name) throws ObjectStoreException {
        String sourceIdentifier = sources.get(name);
        if (sourceIdentifier == null) {
            Item source = createItem("Source");
            source.setAttribute("name", name);
            store(source);
            sourceIdentifier = source.getIdentifier();
            sources.put(name, sourceIdentifier);
        }
        return sourceIdentifier;
    }
    
    private ResultSet queryVariationAnnotation(Connection connection) throws SQLException {
        String query = "SELECT vf.variation_name, "
            + " va.study, va.study_type, va.local_stable_id, va.associated_gene, "
            + " va.associated_variant_risk_allele, va.risk_allele_freq_in_controls, va.p_value, "
            + " p.description,"
            + " s.name"
            + " FROM variation_annotation va, variation_feature vf, phenotype p, source s"
            + " WHERE va.variation_id = vf.variation_id"
            + " AND va.source_id = s.source_id"
            + " AND va.phenotype_id = p.phenotype_id"
            + " ORDER BY va.variation_id";

        Statement stmt = connection.createStatement();
        ResultSet res = stmt.executeQuery(query);
        return res;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getDataSetTitle(int taxonId) {
        return DATASET_TITLE;
    }
}