/*
 * Copyright 2015 JBoss Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.optaplanner.examples.investmentallocation.persistence;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.optaplanner.core.api.domain.solution.Solution;
import org.optaplanner.examples.common.persistence.AbstractTxtSolutionImporter;
import org.optaplanner.examples.investmentallocation.domain.AssetClass;
import org.optaplanner.examples.investmentallocation.domain.AssetClassAllocation;
import org.optaplanner.examples.investmentallocation.domain.InvestmentAllocationSolution;

public class InvestmentAllocationImporter extends AbstractTxtSolutionImporter {

    public static void main(String[] args) {
        InvestmentAllocationImporter importer = new InvestmentAllocationImporter();
        importer.convert("irrinki_1.csv", "irrinki_1.xml");
    }

    public InvestmentAllocationImporter() {
        super(new InvestmentAllocationDao());
    }

    public InvestmentAllocationImporter(boolean withoutDao) {
        super(withoutDao);
    }

    public TxtInputBuilder createTxtInputBuilder() {
        return new InvestmentAllocationInputBuilder();
    }

    public static class InvestmentAllocationInputBuilder extends TxtInputBuilder {

        private InvestmentAllocationSolution solution;

        public Solution readSolution() throws IOException {
            solution = new InvestmentAllocationSolution();
            solution.setId(0L);
            readAssetClassList();
            createAssetClassAllocationList();

            logger.info("InvestmentAllocation {} has {} asset classes.",
                    getInputId(), solution.getAssetClassList().size());
            return solution;
        }

        private void readAssetClassList() throws IOException {
            readConstantLine("ABC Institutional Investor Capital Markets Expectations;*");
            readConstantLine("Asset class;+Correlation;*");
            String headerLine = bufferedReader.readLine();
            String[] headerTokens = splitBySemicolonSeparatedValue(headerLine);
            String headerRegex = "ID;Name;Expected return;Standard deviation(;\\d+)+";
            if (!headerLine.trim().matches(headerRegex)) {
                throw new IllegalArgumentException("Read line (" + headerLine + ") is expected to be a constant regex ("
                        + headerRegex + ").");
            }
            final int ASSET_CLASS_PROPERTIES_COUNT = 4;
            int assetClassListSize = headerTokens.length - ASSET_CLASS_PROPERTIES_COUNT;
            List<AssetClass> assetClassList = new ArrayList<AssetClass>(assetClassListSize);
            Map<Long, AssetClass> idToAssetClassMap = new HashMap<Long, AssetClass>(assetClassListSize);
            for (int i = 0; i < assetClassListSize; i++) {
                AssetClass assetClass = new AssetClass();
                assetClass.setId(Long.parseLong(headerTokens[ASSET_CLASS_PROPERTIES_COUNT + i]));
                assetClassList.add(assetClass);
                idToAssetClassMap.put(assetClass.getId(), assetClass);
            }
            for (String line = bufferedReader.readLine(); line != null; line = bufferedReader.readLine()) {
                String[] tokens = splitBySemicolonSeparatedValue(line, ASSET_CLASS_PROPERTIES_COUNT + assetClassListSize);
                long id = Long.parseLong(tokens[0]);
                AssetClass assetClass = idToAssetClassMap.get(id);
                if (assetClass == null) {
                    throw new IllegalStateException("The assetClass line (" + line
                            + ") has an assetClass id (" + id + ") that is not in the headerLine (" + headerLine + ")");
                }
                assetClass.setName(tokens[1]);
                assetClass.setExpectedReturnMicros(parsePercentageMicros(tokens[2]));
                assetClass.setStandardDeviationRiskMicros(parsePercentageMicros(tokens[3]));
                Map<AssetClass, Long> correlationMicrosMap = new LinkedHashMap<AssetClass, Long>(assetClassListSize);
                for (int i = 0; i < assetClassListSize; i++) {
                    AssetClass other = assetClassList.get(i);
                    long correlationMicros = parsePercentageMicros(tokens[ASSET_CLASS_PROPERTIES_COUNT + i]);
                    correlationMicrosMap.put(other, correlationMicros);
                }
                assetClass.setCorrelationMicrosMap(correlationMicrosMap);
            }
            solution.setAssetClassList(assetClassList);
        }

        private void createAssetClassAllocationList() {
            List<AssetClass> assetClassList = solution.getAssetClassList();
            List<AssetClassAllocation> assetClassAllocationList = new ArrayList<AssetClassAllocation>(assetClassList.size());
            for (AssetClass assetClass : assetClassList) {
                AssetClassAllocation allocation = new AssetClassAllocation();
                allocation.setId(assetClass.getId());
                allocation.setAssetClass(assetClass);
                assetClassAllocationList.add(allocation);
            }
            solution.setAssetClassAllocationList(assetClassAllocationList);
        }

        protected long parsePercentageMicros(String token) {
            BigDecimal micros;
            if (token.endsWith("%")) {
                micros = new BigDecimal(token.substring(0, token.length() - 1))
                        .multiply(new BigDecimal(10000L));
            } else {
                micros = new BigDecimal(token)
                        .multiply(new BigDecimal(1000000L));
            }
            return micros.longValueExact();
        }

    }

}
