package com.example.smartplantcare.ML

import com.example.smartplantcare.data.DiseaseResult

object DiseaseRepository {

    private val database: Map<String, DiseaseResult> = mapOf(



        // ==========================================
        // CHILI DISEASES
        // ==========================================
        "Chili_Bacterial_Spot" to DiseaseResult(
            diseaseName = "Bacterial Spot (Chili)",
            description = "Small, dark, water-soaked spots appear first on older lower leaves, rapidly turning brown to black with yellow halos surrounding them. Heavily infected leaves drop early, exposing the plant to stress and sunscald. Fruit develops raised, rough, scab-like lesions that reduce marketability even when the plant survives.",
            causes = "Caused by Xanthomonas euvesicatoria and related Xanthomonas species. Bacteria enter through natural leaf openings (stomata) or wounds and spread explosively through splashing rain, wind-driven water, contaminated tools, and bare hands. Disease develops fastest at 24–30°C with high rainfall or overhead irrigation. Infected seed and diseased transplants are the most common entry points into a new planting.",
            treatment = "Apply a copper hydroxide + mancozeb tank mix as the primary chemical treatment  copper alone increasingly loses effectiveness against resistant Xanthomonas strains, and mancozeb prevents resistance buildup. Spray on a 7–10 day interval, shortening to 5–7 days during rainy weather. Strictly avoid handling plants when foliage is wet. Where resistant varieties (e.g. Antebellum, Green Machine, Prowler) are available, they provide the most cost-effective long-term control and eliminate the need for routine spraying.",
            preventionTips = "Use certified disease-free seed or treat seed with hot water (52°C for 30 minutes). Never transplant visibly infected seedlings. Switch from overhead to drip/base irrigation. Space plants wide enough for airflow and sanitize tools between plants with 10% bleach or 70% alcohol.",
            sevenDayPlan = listOf(
                "Day 1: Isolate the plant. Prune and bag all spotted leaves. Disinfect shears with 70% alcohol between every cut. Do not leave pruned material on the ground.",
                "Day 2: Apply a copper hydroxide + mancozeb tank mix at the label-recommended rate, covering both leaf surfaces. Spray in the early morning before heat sets in to avoid phytotoxicity.",
                "Day 3: Stop all overhead watering immediately. Switch to drip or soil-level irrigation only and maintain this for the rest of the week and beyond.",
                "Day 4: Inspect new growth for fresh water-soaked lesions. Early spread detection is critical — bacteria already inside leaf tissue cannot be eliminated by copper; suppression and sanitation are the only tools.",
                "Day 5: Re-apply copper + mancozeb if rainfall occurred since Day 2 (copper washes off). Do not exceed 3 copper applications per week — excess copper causes phytotoxicity.",
                "Day 6: Clear and destroy all fallen leaf debris around the plant base. Debris harbors bacteria across seasons and is a primary re-infection source.",
                "Day 7: Assess overall damage. If more than 30% of the plant is affected, remove it entirely to protect surrounding plants. If contained, resume base-only watering and continue weekly copper + mancozeb sprays as a maintenance rotation."
            )
        ),

        "Chili_Cercospora_Leaf_Spot" to DiseaseResult(
            diseaseName = "Cercospora Leaf Spot (Chili)",
            description = "Circular spots with a distinctive light gray-to-tan papery center and dark brown border — the classic 'frog-eye' pattern — form first on older lower leaves. As spots multiply, the central tissue may drop out, leaving shot-hole openings. Leaves yellow and drop prematurely, and in severe outbreaks the entire lower canopy can defoliate, exposing fruit to sunscald and dramatically cutting photosynthesis.",
            causes = "Caused by the fungus Cercospora capsici. Wind and splashing water disperse spores, and infection progresses fastest when leaf surfaces remain wet for 12 or more hours at temperatures above 25°C. Dense plantings, poor airflow, and overhead irrigation extend leaf wetness periods and worsen outbreaks. Spores also persist on infected debris in the soil between seasons.",
            treatment = "Start a fungicide spray program at the very first sign of disease — waiting until infection is established reduces efficacy. Copper-based fungicides (copper hydroxide, copper oxychloride) provide broad-spectrum control and are the best starting point. For more active infections, use chlorothalonil or mancozeb as contact protectants on 7–10 day intervals, or DMI (Group 3) fungicides such as propiconazole or trifloxystrobin for systemic action. Rotate between fungicide groups each spray cycle to prevent resistance. Hot water seed treatment (52°C for 30 minutes) significantly reduces spore load on seed.",
            preventionTips = "Use disease-free or hot-water-treated seed. Practice at least a 2–3 year crop rotation away from solanaceous crops. Use drip irrigation instead of overhead watering. Inspect and reject diseased transplants before planting. Destroy crop debris by soil incorporation or burning after harvest.",
            sevenDayPlan = listOf(
                "Day 1: Remove and dispose of all leaves showing frog-eye spots. Do not compost them — spores survive in plant debris. Disinfect tools after each cut.",
                "Day 2: Apply copper hydroxide or chlorothalonil fungicide as a full-coverage spray, hitting both upper and lower leaf surfaces where spores form.",
                "Day 3: Thin out dense foliage around the affected plant to reduce canopy humidity and improve airflow. Remove leaves touching each other.",
                "Day 4: Check lower leaves specifically — Cercospora starts at the base and moves upward. Mark or note new lesions to track spread.",
                "Day 5: Water strictly at soil level only. Keep all foliage dry through the rest of the treatment week and permanently going forward.",
                "Day 6: Apply a potassium-rich foliar feed to strengthen leaf tissue and support the plant's ability to push new healthy growth above the infected zone.",
                "Day 7: Re-apply fungicide on the 7–10 day schedule. Rotate to a different fungicide group (e.g. switch from chlorothalonil to propiconazole) to reduce resistance risk."
            )
        ),

        "Chili_Curl_Virus" to DiseaseResult(
            diseaseName = "Leaf Curl Virus (Chili)",
            description = "Leaves curl sharply upward and inward, become thickened, leathery, and brittle, with veins swollen and internodes shortened — giving the plant a stunted, bunched appearance. New growth is severely reduced. Flowering drops sharply and fruit set fails almost entirely in plants infected early, often leading to complete yield loss. Older infected leaves develop a rough texture and older leaves become brittle.",
            causes = "Caused by begomoviruses (family Geminiviridae), most commonly Chili Leaf Curl Virus (ChiLCV), transmitted exclusively by the whitefly Bemisia tabaci while feeding on plant sap. The virus cannot spread through soil, water, or casual contact — whiteflies are the sole vector. The virus also persists in alternative hosts including tobacco, tomato, and surrounding weeds, making weed control critical. Warm, dry conditions favor rapid whitefly population buildup.",
            treatment = "No chemical cures the virus once a plant is infected. All treatment focuses on the whitefly vector and on removing infected plants that act as virus reservoirs. For whitefly control: neem oil (azadirachtin-based) and insecticidal soap are first-line organic options. For chemical control, use acetamiprid, thiamethoxam, or diafenthiuron — rotate actives every 2 sprays to prevent whitefly resistance, which develops rapidly. Avoid overusing organophosphates, carbamates, and pyrethroids as these eliminate natural whitefly predators and worsen populations long-term. Reflective silver mulch before canopy cover has documented efficacy in delaying infection by repelling whiteflies.",
            preventionTips = "Install yellow sticky traps at canopy height for early population monitoring. Use reflective silver plastic mulch before transplanting. Plant resistant or tolerant chili varieties where available. Remove and destroy infected plants immediately — they are whitefly and virus reservoirs for the entire field.",
            sevenDayPlan = listOf(
                "Day 1: Inspect all leaf undersides for whitefly adults nymphs and eggs. If curling is severe and the plant is stunted, bag and uproot it same-day  it is now actively infecting neighboring plants.",
                "Day 2: Dispose of the removed plant away from the garden never compost virus-infected material. If the plant was marginally infected, spray neem oil directly on whitefly colonies on the undersides.",
                "Day 3: Install yellow sticky traps at canopy height around all remaining chili plants to monitor whitefly population levels.",
                "Day 4: Apply neem oil or insecticidal soap as a preventive spray on all surrounding healthy chili plants. Cover leaf undersides thoroughly — this is where whiteflies feed and lay eggs.",
                "Day 5: Check trap counts. Rising numbers indicate the infestation source (a nearby reservoir plant or weed) is still active. If counts remain high, escalate to acetamiprid or thiamethoxam.",
                "Day 6: Clear all weeds and volunteer plants within 2 meters of the chili planting — these are common overwintering hosts for both whiteflies and the virus.",
                "Day 7: Re-apply neem oil or rotate to a systemic insecticide (acetamiprid/thiamethoxam). Whitefly eggs hatch in 5–7 days, so a single spray never breaks the cycle — repeat applications are non-negotiable."
            )
        ),

        "Chili_Healthy_Leaf" to DiseaseResult(
            diseaseName = "Healthy Chili Plant",
            description = "Leaves are uniformly deep green, firm, and free of spots, curling, discoloration, or physical damage. Growth is steady and upright with no stunting. Flowering and fruit set proceed normally with no abnormal drop.",
            causes = "The result of consistently good agricultural practice: adequate plant spacing for airflow, balanced NPK fertilization, correct watering depth and timing, 6+ hours of direct sun daily, and regular scouting that catches pests or disease at the earliest possible stage before they establish.",
            treatment = "No active disease is present. The goal is a proactive maintenance program that sustains plant vigor and keeps disease pressure low — healthy soil, balanced nutrition, and consistent monitoring are more protective than any reactive treatment.",
            preventionTips = "Maintain consistent soil-level watering and balanced bi-weekly fertilization. Inspect leaf undersides weekly for whiteflies, aphids, and early fungal spotting even when the plant looks completely healthy. Keep the area around plant bases clean and weed-free.",
            sevenDayPlan = listOf(
                "Day 1: Water deeply at soil level and ensure the plant receives at least 6 hours of direct sunlight. Inspect both leaf surfaces for early pest or disease signs.",
                "Day 2: Check soil moisture before watering again — chili prefers evenly moist but never waterlogged soil. Overwatering is a common trigger for root stress and secondary disease.",
                "Day 3: Apply a balanced 14-14-14 NPK fertilizer if the last feeding was more than 2 weeks ago. Consistent nutrition is a plant's primary defense against infection.",
                "Day 4: Scout for whiteflies (leaf undersides), aphids (new growth tips), and early Cercospora or bacterial spotting on lower leaves.",
                "Day 5: Prune any yellowing lower leaves to improve airflow through the base of the canopy and reduce humidity at leaf level.",
                "Day 6: Water again at soil level, adjusting amount based on rainfall received during the week. Avoid wetting foliage.",
                "Day 7: Conduct a full plant inspection — no new spotting, no curling, no pest clusters. If all is clear, continue the same schedule next week."
            )
        ),

        "Chili_Nutrition_Deficiency" to DiseaseResult(
            diseaseName = "Nutrient Deficiency (Chili)",
            description = "Symptoms vary clearly by the nutrient involved: uniform pale yellowing from the lower leaves upward indicates nitrogen deficiency; purple or reddish discoloration on leaf undersides and petioles signals phosphorus deficiency; yellowing between veins while veins remain green is a classic sign of magnesium or iron deficiency. In all cases, plant growth is stunted and fruit yield and quality decline regardless of which specific nutrient is limiting.",
            causes = "Primary drivers include poor or depleted soil, incorrect soil pH (chili can no longer efficiently absorb nutrients outside the 6.0–6.8 range regardless of soil levels), and waterlogged or compacted roots that physically cannot take up nutrients. Excessive watering that continually leaches nutrients from the root zone is another common cause.",
            treatment = "Match the fertilizer to the confirmed deficiency: pale-yellow overall growth → apply a high-nitrogen fertilizer (20-10-10 or urea); purple leaf undersides → use a high-phosphorus blend (10-52-10); interveinal yellowing → apply magnesium sulfate (Epsom salts at 15g/L as foliar spray) for magnesium deficiency or chelated iron for iron deficiency. For general deficiency without a clear type, a balanced 14-14-14 or 20-20-20 NPK provides all three macronutrients. Always start at half-strength to avoid root burn on a stressed plant. Correct soil pH before fertilizing if it is outside 6.0–6.8 — nutrients won't be absorbed regardless of how much fertilizer is applied at the wrong pH.",
            preventionTips = "Test soil pH before planting and amend with lime (to raise pH) or elemental sulfur (to lower pH) to maintain 6.0–6.8. Incorporate compost or aged manure to improve cation exchange capacity. Establish a bi-weekly balanced fertilizer schedule from transplanting onward rather than reacting to deficiency symptoms.",
            sevenDayPlan = listOf(
                "Day 1: Diagnose the specific deficiency: pale yellow from lower leaves up = nitrogen; purple leaf undersides = phosphorus; yellowing between green veins = magnesium or iron.",
                "Day 2: Apply the matching liquid fertilizer at half-strength to avoid burning stressed roots. For suspected magnesium deficiency, spray Epsom salt solution (15g/L) directly on leaves for faster uptake.",
                "Day 3: Check that the soil is not waterlogged. Saturated roots cannot absorb nutrients regardless of how much fertilizer is applied — fix drainage before anything else.",
                "Day 4: Lightly work compost or aged manure into the top layer around the drip line to improve long-term soil structure and nutrient-holding capacity.",
                "Day 5: Observe new leaf growth for color change — nutrient correction always shows first in new leaves, not in existing yellowed ones. Yellowed old leaves will not recover.",
                "Day 6: If the Day 2 application showed visible improvement in new growth, apply the second half-strength dose. If not, re-check soil pH — it may be the root cause.",
                "Day 7: Test soil pH if correction has not improved new leaf color. Set a recurring bi-weekly full-strength feeding schedule going forward to prevent recurrence."
            )
        ),

        "Chili_White_Spot" to DiseaseResult(
            diseaseName = "White Spot / Powdery Mildew (Chili)",
            description = "White powdery or mealy-looking patches spread across the upper surface of leaves, occasionally extending to undersides and stems. Unlike most fungal diseases, it appears on dry, intact leaf surfaces rather than on wet or damaged tissue. Heavily affected leaves curl, turn yellow, and drop early. If the white marks are raised, rough, or silvery rather than powdery, the cause may be thrips feeding rather than mildew.",
            causes = "Powdery appearance is almost always Leveillula taurica, a fungus uniquely capable of infecting leaves without free water on the surface — it spreads fastest in warm days (25–30°C) combined with high relative humidity and stagnant air inside dense canopies. Raised, silvery-white scarring is typically thrips (Thrips tabaci or Frankliniella spp.) feeding damage, which leaves a physically different, rough texture compared to the wipe-able powder of true mildew.",
            treatment = "For confirmed powdery mildew: spray sulfur-based fungicide (best applied at dusk — sulfur can scorch leaves in direct sun or heat above 32°C) or potassium bicarbonate as a contact option. A 1:9 milk-to-water foliar spray is a documented low-cost alternative for mild cases and works by triggering plant immune response. For confirmed thrips: apply spinosad or abamectin, targeting the growing tips and leaf undersides where thrips congregate and feed. Rotate actives between sprays to prevent resistance.",
            preventionTips = "Keep foliage dry and ensure good airflow between plants. Avoid excess nitrogen fertilization — lush, soft new growth is the most susceptible tissue to both mildew and thrips. For thrips, yellow and blue sticky traps provide early population monitoring.",
            sevenDayPlan = listOf(
                "Day 1: Diagnose the cause before spraying. Rub a white patch with your finger — if it wipes off as powder it is mildew; if it leaves a rough, hard scar it is thrips feeding damage. Correct identification drives correct treatment.",
                "Day 2: For mildew: spray sulfur fungicide at dusk or potassium bicarbonate in the morning, covering both leaf surfaces. For thrips: apply spinosad to all growing tips and leaf undersides.",
                "Day 3: Prune and remove the most heavily affected leaves to reduce spore load (mildew) or feeding sites (thrips). Dispose of removed material away from the garden.",
                "Day 4: Check spread rate and direction — mildew spreads fast in humid, stagnant-air conditions. Thin nearby foliage if the canopy is dense.",
                "Day 5: Reduce nitrogen fertilizer temporarily. High nitrogen produces soft, fast-growing tissue that is the primary target for both mildew spores and thrips.",
                "Day 6: For mildew: if the first application did not visibly reduce white coverage, apply potassium bicarbonate as a follow-up with a different mode of action. For thrips: re-spray spinosad — thrips eggs hatch in 5–7 days.",
                "Day 7: Re-apply the confirmed treatment. Set a weekly monitoring check going forward — both mildew and thrips can re-establish quickly from spores or eggs that survive initial treatment."
            )
        ),

// ==========================================
// EGGPLANT DISEASES
// ==========================================

        "Eggplant_Cercospora Leaf Spot" to DiseaseResult(
            diseaseName = "Cercospora Leaf Spot (Eggplant)",
            description = "Round to irregular spots with gray or pale papery centers and darker brown-to-purple borders develop on eggplant leaves, typically starting on older foliage near the plant base. Spots can merge into large dead patches, and heavy infections cause premature leaf drop that exposes developing fruit directly to intense sun, causing sunscald and reducing marketable yield significantly.",
            causes = "Caused by Cercospora fungus, which thrives in warm, humid conditions (25–30°C) and extended periods of leaf wetness. Spores travel by wind and water splash, and they survive in fallen crop debris in the soil between growing seasons — making field sanitation and crop rotation critical management tools alongside fungicide.",
            treatment = "Apply copper oxychloride or chlorothalonil fungicide at the first visible lesion. For active or spreading infections, mancozeb provides effective contact protection and is easily tank-mixed with copper. Follow a 7–10 day spray interval and shorten to 5–7 days during extended wet weather. Apply mulch at the base to physically block soil-borne spore splash onto lower leaves — this is one of the highest-impact cultural steps. Rotate between fungicide groups to prevent resistance.",
            preventionTips = "Use drip irrigation exclusively and avoid any overhead watering. Maintain at least 45–60 cm plant spacing to promote airflow and faster canopy drying. Rotate eggplant out of the same soil for a minimum of 2–3 seasons. Remove and destroy all crop debris after harvest — do not compost it.",
            sevenDayPlan = listOf(
                "Day 1: Remove all spotted leaves immediately. Do not compost them — Cercospora spores persist in plant debris. Dispose away from the garden.",
                "Day 2: Apply copper oxychloride or chlorothalonil fungicide as a full-coverage spray, ensuring thorough coverage of leaf undersides where spores develop.",
                "Day 3: Lay a 5–7 cm layer of straw or plastic mulch at the plant base to physically block soil-borne spore splash onto lower leaves.",
                "Day 4: Check for new lesions, particularly after any rain. If new spots appear on upper leaves, the infection is still spreading and the spray interval should be shortened.",
                "Day 5: Apply a balanced fertilizer to help the plant push healthy new foliage above the infected zone. A well-nourished plant has stronger natural disease resistance.",
                "Day 6: Confirm that drainage is adequate and water is not pooling at the base. Waterlogged roots suppress the plant's immune response and accelerate disease progression.",
                "Day 7: Re-apply fungicide on the 7–10 day cycle. Rotate to mancozeb if chlorothalonil was used on Day 2, or vice versa, to prevent resistance buildup."
            )
        ),

        "Eggplant_Healthy Leaf" to DiseaseResult(
            diseaseName = "Healthy Eggplant",
            description = "Foliage is deep, uniform green, firm, and free of spots, holes, curling, or discoloration. Stems are upright and sturdy. Growth is steady with normal flower production and fruit set, and fruit develops without deformity, cracking, or blemishes.",
            causes = "The result of consistently good agricultural management: proper plant spacing (45–60 cm minimum) for airflow, correct irrigation depth and timing, balanced fertilization, adequate sunlight (6+ hours daily), and regular scouting that identifies pest and disease pressure early before establishment.",
            treatment = "No active disease is present. Maintain the current routine and focus on prevention: consistent feeding schedule, soil-level watering, and weekly leaf inspections on both surfaces.",
            preventionTips = "Keep 45–60 cm spacing between plants for airflow. Maintain thick mulch at the base to prevent soil splash. Scout weekly for flea beetles, aphids, and the earliest signs of fungal lesions on lower leaves.",
            sevenDayPlan = listOf(
                "Day 1: Water deeply at soil level and inspect both sides of leaves carefully for early pest signs (shot-holes, aphid clusters, stippling) or early fungal spotting.",
                "Day 2: Check mulch at the plant base — top up with straw or wood chips if thin. Mulch blocks soil-borne spore splash and retains root-zone moisture.",
                "Day 3: Scout for flea beetle shot-holes on leaf surfaces and aphid colonies on new growth. Early detection at this stage requires minimal treatment.",
                "Day 4: Apply a balanced 14-14-14 NPK fertilizer if the last feeding was more than 2 weeks ago. Consistent nutrition maintains plant resistance to stress-triggered infection.",
                "Day 5: Water again at soil level following the established twice-weekly schedule. Adjust volume based on any rain received during the week.",
                "Day 6: Prune any lower leaves touching the soil surface — ground contact is a direct route for soil-borne fungal spores to enter leaf tissue.",
                "Day 7: Full plant inspection to confirm no new spotting, pest activity, or wilting. If all clear, continue the same maintenance routine through the following week."
            )
        ),

        "Eggplant_Insect Pest Disease" to DiseaseResult(
            diseaseName = "Insect Pest Infestation (Eggplant)",
            description = "Visible feeding damage appears as small round shot-holes across leaf surfaces (flea beetles), large ragged sections of stripped foliage (hornworms), or curled and sticky new growth covered with soft-bodied insects (aphids). Honeydew secreted by aphids encourages secondary sooty mold growth. Heavy infestations can defoliate a plant rapidly and, in the case of aphids, may also transmit plant viruses.",
            causes = "Flea beetles (Epitrix spp.) chew characteristic small round shot-holes and are worst in warm, dry weather on young transplants. Aphids cluster on tender new growth and reproduce extremely fast in mild temperatures — populations can explode within days. Hornworms (Manduca spp.) are large green caterpillars that feed heavily at night and can strip large sections of foliage within 48 hours if not caught early.",
            treatment = "Identify the pest before treating — different pests need different products. Flea beetles: spinosad applied as a foliar spray or kaolin clay as a physical barrier. Aphids: insecticidal soap (most effective direct contact) or neem oil (7-day residual); avoid broad-spectrum insecticides that kill beneficial predators. Hornworms: hand-pick manually (they are large and visible) or apply Bacillus thuringiensis var. kurstaki (Bt) as a caterpillar-specific biological insecticide that does not harm beneficial insects. Repeat applications every 5–7 days are standard for all three pests.",
            preventionTips = "Use floating row covers on young transplants until flower set. Encourage beneficial predators — ladybugs and lacewings naturally suppress aphid populations. Install yellow sticky traps to monitor flying adult flea beetles and winged aphids early.",
            sevenDayPlan = listOf(
                "Day 1: Identify the pest precisely: shot-holes flea beetles; clusters of soft-bodied insects on new growth  aphids; large green caterpillars with ragged feeding  hornworms.",
                "Day 2: Apply the matched treatment: spinosad for flea beetles; insecticidal soap directly on aphid colonies (ensure contact) hand-pick hornworms and drop into soapy water.",
                "Day 3: Inspect leaf undersides for eggs. Aphid eggs are tiny and oval hornworm eggs are round and pale green. Crush all found eggs to break the next generation.",
                "Day 4: Clear weeds around the plant base  many pest species overwinter in weed roots and soil debris directly adjacent to the planting.",
                "Day 5: Re-apply the primary treatment. Insecticidal soap breaks down quickly; spinosad has a 5–7 day residual. Bt must be re-applied after rain. All three pests need follow-up sprays.",
                "Day 6: Set up yellow sticky traps for flying adults (effective for winged aphids and flea beetles). Check traps after 24 hours to gauge population level.",
                "Day 7: Full plant inspection to confirm the pest cycle is broken. If pest activity has significantly dropped, resume normal care. If still active, escalate to a systemic insecticide appropriate to the pest type."
            )
        ),

        "Eggplant_Leaf Spot Disease" to DiseaseResult(
            diseaseName = "General Leaf Spot (Eggplant)",
            description = "Irregular brown or black spots form across the leaf surface. Fungal spots typically have a dry, papery texture with a yellow halo (Alternaria or Phomopsis); bacterial spots appear water-soaked, greasy, and translucent, often without a distinct raised margin. Both types cause yellowing and early leaf drop, weakening the plant's ability to support fruit through photosynthesis.",
            causes = "Fungal causes include Alternaria solani and Phomopsis vexans, both favored by warm humid conditions and splashing water. Water-soaked, greasy-looking lesions instead point to bacterial pathogens (Pseudomonas or Xanthomonas species) common in prolonged wet weather. Distinguishing between fungal and bacterial is critical because the treatments are different.",
            treatment = "For fungal leaf spot: remove infected leaves, then apply mancozeb or chlorothalonil at 7–10 day intervals. For bacterial leaf spot: use a copper-based bactericide (copper hydroxide or copper oxychloride) instead — standard fungicides have no effect on bacteria. If both are suspected, a copper + mancozeb tank mix covers both simultaneously. Avoid applying any foliar spray when rain is forecast within 4 hours.",
            preventionTips = "Space plants at least 45–60 cm apart for adequate airflow. Improve drainage if water stands around plant bases. Rotate eggplant away from the same bed for at least 2 seasons. Always water at the soil level and keep foliage dry.",
            sevenDayPlan = listOf(
                "Day 1: Prune all infected leaves. Disinfect cutting tools with 70% alcohol between each cut — both fungal spores and bacteria spread easily on contaminated tool edges.",
                "Day 2: Assess lesion type: dry and papery with yellow halo = fungal (apply mancozeb). Water-soaked, greasy, translucent = bacterial (apply copper). If unsure, apply a copper + mancozeb tank mix to cover both.",
                "Day 3: Water only at the soil level — keep foliage completely dry for the entire treatment week. Wet foliage accelerates both fungal spore germination and bacterial spread.",
                "Day 4: Monitor spread to healthy leaves. Note whether new lesions look fungal (dry, ringed, papery) or bacterial (wet, greasy, spreading fast in cool wet weather).",
                "Day 5: Apply the second fungicide round per the 5–7 day label interval if conditions remain wet. Ensure full coverage including leaf undersides.",
                "Day 6: Ensure the plant receives maximum possible sun exposure to help the canopy dry quickly after any rain or dew.",
                "Day 7: Assess spread — if lesions are contained and not advancing, continue the 7–10 day spray rotation. If still expanding rapidly, adjust treatment: switch to copper if fungal treatment is not working, suggesting a bacterial component."
            )
        ),

        "Eggplant_Mosaic Virus Disease" to DiseaseResult(
            diseaseName = "Mosaic Virus (Eggplant)",
            description = "Leaves develop a mottled pattern of alternating light and dark green patches, often with distorted, wrinkled, or curled leaf margins. New growth is severely stunted, and internodes shorten. Fruit that does form is typically small, misshapen, and discolored. The plant rarely dies outright but essentially stops being productive.",
            causes = "Caused by multiple potyviruses (Potato Virus Y, Tobacco Mosaic Virus, Cucumber Mosaic Virus) spread primarily by aphids during sap-feeding. Critically, insecticides do not effectively prevent transmission of these viruses — aphids inject virus into plants within seconds of landing, before insecticide residue can kill them. Reflective silver mulch is more effective than insecticides at preventing aphid-borne virus transmission because it repels aphids before they land. The virus also spreads via contaminated tools and hands.",
            treatment = "No chemical product cures a virus-infected plant. Remove and destroy infected plants immediately — they are a permanent virus reservoir for aphids to continue spreading from. Control the aphid vector on surrounding healthy plants using neem oil or insecticidal soap (primarily as a deterrent, not as a cure). Reflective silver polyethylene mulch laid before transplanting is one of the most effective preventive tools documented — it delays infection by repelling aphids. Once more than 60% of the soil surface is covered by eggplant foliage, reflective mulch loses effectiveness.",
            preventionTips = "Install reflective silver mulch before transplanting as the single most effective aphid-borne virus prevention tool. Control aphids on all surrounding plants. Pull out and destroy infected plants as soon as symptoms appear. Sanitize all tools with 1:4 bleach-to-water solution between uses.",
            sevenDayPlan = listOf(
                "Day 1: Remove the infected plant immediately. Mosaic virus does not recover and only spreads further over time. Every day of delay allows more aphid feeding and more plants at risk.",
                "Day 2: Bag the removed plant tightly and dispose of it in the trash — never compost virus-infected plant material. Virus particles survive composting.",
                "Day 3: Treat all surrounding healthy eggplant plants with neem oil to knock down the aphid vector population. Cover leaf undersides thoroughly.",
                "Day 4: Sanitize all tools (stakes, ties, pruning shears, cages) that contacted the infected plant using a 10% bleach solution or 70% alcohol. Allow tools to dry before reuse.",
                "Day 5: Closely inspect neighboring plants for early mottling — symptoms can take 1–2 weeks to appear after transmission occurs.",
                "Day 6: Remove all weeds within 2 meters of the planting — weeds are common overwintering hosts for both aphid populations and the viruses they carry.",
                "Day 7: Re-inspect the full bed. Consider installing reflective silver mulch around any remaining plants to reduce future aphid landing. Plan for resistant varieties or early planting in the next crop cycle."
            )
        ),

        "Eggplant_Powdery Mildew" to DiseaseResult(
            diseaseName = "Powdery Mildew (Eggplant)",
            description = "White, powdery fungal patches spread across the upper surfaces of leaves, sometimes covering entire leaves in severe cases. Affected leaves yellow, curl upward, and drop early. Unlike most fungal diseases, powdery mildew does not require free water on leaf surfaces to infect — it spreads in warm days with high humidity and can be widespread even during dry weather if airflow around the canopy is poor.",
            causes = "Caused primarily by Leveillula taurica in eggplant, a fungal pathogen unique in that it does not need wet leaves to infect or spread — only high relative humidity and warm temperatures (20–30°C) combined with poor air circulation. Overcrowded plantings and excessive nitrogen fertilization (which produces soft, fast-growing tissue) significantly increase susceptibility.",
            treatment = "Potassium bicarbonate spray is the most effective and fastest-acting contact treatment and is safe for use close to harvest. Sulfur-based fungicide is highly effective but must be applied at dusk or in the early morning — sulfur causes severe leaf scorch at temperatures above 32°C or in direct sun. A 1:9 milk-to-water foliar spray is a documented, evidence-based low-cost option for mild to moderate infection (the proteins in milk trigger plant immune response). Repeat treatment every 7 days. A single spray rarely achieves full control.",
            preventionTips = "Maintain 45–60 cm plant spacing and prune lower leaves to maximize airflow through the canopy. In enclosed growing spaces (greenhouses, tunnels), add fans or ventilation to keep humidity below 85%. Avoid excess nitrogen fertilizer, which promotes the soft new growth that mildew colonizes first.",
            sevenDayPlan = listOf(
                "Day 1: Apply potassium bicarbonate spray or sulfur fungicide (sulfur at dusk only), covering both upper and lower leaf surfaces thoroughly. Add a spreader-sticker agent to improve adhesion.",
                "Day 2: Prune the most heavily white-coated leaves to physically reduce spore load. This is especially important in enclosed spaces where spores recirculate.",
                "Day 3: Thin surrounding foliage to improve airflow through the canopy. In a greenhouse or tunnel, open vents or run fans continuously — ventilation is as important as any spray.",
                "Day 4: Check whether white coating is receding on treated leaves. If still advancing, consider adding a milk spray (1:9 milk:water) as a supplemental immune booster between fungicide applications.",
                "Day 5: Water at the base only. Mildew does not need wet leaves to spread, but water-stressed plants are significantly more vulnerable to infection.",
                "Day 6: Re-apply fungicide — a single treatment rarely achieves complete control. If sulfur was used Day 1, switch to potassium bicarbonate this time to avoid resistance.",
                "Day 7: Inspect all new growth — the youngest leaves are most susceptible to fresh infection cycles. If spread is controlled, transition to a 10–14 day preventive spray schedule."
            )
        ),

// ==========================================
// TOMATO DISEASES
// ==========================================

        "Tomato_Bacterial_spot" to DiseaseResult(
            diseaseName = "Bacterial Spot (Tomato)",
            description = "Small, dark, greasy-looking spots appear on leaves, stems, and fruit — often with a yellow halo on leaves and raised, scab-like lesions on green fruit that reduce marketability. Heavy leaf infection causes defoliation and exposes fruit to sunscald. Spots are most commonly seen first on older, lower leaves before progressing upward.",
            causes = "Caused by Xanthomonas perforans (the dominant species on tomato) spread by splashing water, wind-driven rain, contaminated hands, and tools. Warm, wet weather at 24–30°C drives rapid spread. The disease most commonly enters a new planting through infected seed or infected transplants. Once on tomato fruit, red fruit cannot be infected (high acid content inhibits bacteria), but green fruit can develop scab lesions.",
            treatment = "Apply a copper hydroxide + mancozeb tank mix — copper alone is increasingly ineffective against resistant strains and the tank mix with mancozeb helps prevent resistance. Spray on a 7–10 day interval; shorten to 5–7 days during wet weather. Never apply sprays when foliage is wet or rain is forecast within 4 hours. Avoid handling plants when leaves are wet. Begin sprays at first symptom appearance and continue through the season.",
            preventionTips = "Use certified disease-free seed or treat seed with hot water (52°C for 30 minutes). Never transplant seedlings showing symptoms. Switch from overhead to drip irrigation. Sanitize all stakes, cages, and cutting tools with bleach or alcohol between plants. Rotate tomatoes away from the same bed for at least 2 years.",
            sevenDayPlan = listOf(
                "Day 1: Prune infected foliage and place immediately in a bag — do not drop on the ground. Disinfect hands and all tools with 70% alcohol before touching any other plants.",
                "Day 2: Apply a copper hydroxide + mancozeb tank mix spray, covering both upper and lower leaf surfaces and stems. Apply in the early morning and avoid overhead spraying.",
                "Day 3: Switch permanently to base-only watering. Stop all overhead irrigation for the rest of the season wet foliage is the primary spread mechanism.",
                "Day 4: Check green fruit for scab lesions. Fruit spotting confirms the infection is still active and airborne. Continue the spray schedule without interruption.",
                "Day 5: Re-spray if rainfall has occurred since the Day 2 application — copper washes off and must be re-applied after rain to maintain the protective residue.",
                "Day 6: Apply a low-nitrogen fertilizer (avoid high-nitrogen formulations). Excess nitrogen produces lush, soft growth that is more susceptible to bacterial infection.",
                "Day 7: Assess spread. If new lesions are still appearing on upper leaves, continue the copper + mancozeb rotation weekly. If spread has stopped, shift to a 10–14 day maintenance schedule."
            )
        ),

        "Tomato_Early_blight" to DiseaseResult(
            diseaseName = "Early Blight (Tomato)",
            description = "Dark brown lesions with a distinctive concentric ring 'bullseye' or 'target' pattern appear first on the oldest lower leaves, then progress upward as the season advances. Leaves yellow around lesions and drop, leaving the lower plant bare. In advanced cases, stem lesions (dark, sunken, and collar-like near soil level) and fruit lesions (sunken, dark, leathery) can also develop.",
            causes = "Caused by Alternaria linariae (formerly A. solani) fungus, which overwinters in infected soil debris and on volunteer tomato plants, then splashes up onto lower leaves during rain or irrigation. The fungus requires alternating wet and dry periods at warm temperatures (24–29°C) for rapid spread. Spores spread primarily by water splash and air currents. Plants under nitrogen stress are significantly more susceptible.",
            treatment = "Apply protectant fungicides preventively before symptoms appear in areas with disease history, or at first visible lesion. Chlorothalonil and mancozeb are the most widely recommended contact protectants — apply on a strict 7–10 day interval and re-apply after rain. For established lesions on multiple leaves, escalate to systemic fungicides: azoxystrobin (FRAC Group 11) or difenoconazole (FRAC Group 3) provide curative activity inside the plant. Rotate between FRAC groups every 2 applications to prevent resistance. Mulch the base to prevent soil-spore splash — this is one of the most effective cultural interventions.",
            preventionTips = "Remove the lowest 3–5 leaf branches at transplanting and maintain a clean lower canopy all season. Mulch heavily around plant bases with straw or plastic film. Practice 2–3 year crop rotation. Maintain adequate nitrogen nutrition — nitrogen-deficient plants are far more susceptible. Trellis plants to maximize airflow.",
            sevenDayPlan = listOf(
                "Day 1: Prune all lower leaves touching or within 15 cm of the soil early blight always starts at the base and mulch cannot stop it once lower leaves are already infected.",
                "Day 2: Apply chlorothalonil or mancozeb (contact fungicide) if symptoms are mild, or azoxystrobin (systemic) if lesions are already visible on multiple leaves. Cover both leaf surfaces completely.",
                "Day 3: Lay straw or plastic mulch 5–8 cm deep around the plant base to block soil-borne spore splash. This single step can dramatically slow early blight progression.",
                "Day 4: Switch to drip or soil-level watering only. No overhead water for the rest of the season.",
                "Day 5: Check upper leaves for new bullseye lesions. If the disease is advancing upward rapidly despite spraying, escalate to a systemic fungicide on the next application.",
                "Day 6: Apply a balanced NPK fertilizer — maintaining plant vigor and adequate nitrogen significantly reduces early blight susceptibility.",
                "Day 7: Apply the second fungicide rotation on the 7–10 day schedule. If chlorothalonil was used on Day 2, switch to azoxystrobin this time. Alternate between fungicide groups for the rest of the season."
            )
        ),

        "Tomato_Late_blight" to DiseaseResult(
            diseaseName = "Late Blight (Tomato)",
            description = "Large, dark, water-soaked patches spread rapidly across leaves and stems — sometimes with a pale green-yellow border — and white fuzzy sporulation appears on the underside in humid conditions. Fruit develops firm, brown, greasy-looking rot that renders it completely unmarketable. Late blight can move from the first visible lesion to complete plant destruction within 3–5 days under favorable cool, wet conditions. The same pathogen devastates potatoes.",
            causes = "Caused by Phytophthora infestans, an oomycete (water mold, not a true fungus), which spreads explosively in cool (15–21°C), wet, humid weather. Spores travel by wind up to 30–40 miles from infected fields. The same pathogen affects potato, so proximity to infected potato crops or fields is a significant risk factor. The disease does not overwinter in soil without a living host but arrives on wind or infected transplants each season.",
            treatment = "Act immediately at the first lesion — late blight escalates within days. If caught very early (single lesion on one leaf), remove the infected leaf and spray chlorothalonil or a copper fungicide the same day. Apply preventive sprays to all remaining plants on a 5–7 day schedule during cool, wet weather. Once lesions have spread to more than 3–4 leaves or stems, chemical treatment cannot save the plant — remove and destroy it immediately to protect nearby plantings. Check the local blight forecasting network (USABlight or regional equivalent) for early warnings during cool wet weather.",
            preventionTips = "Plant resistant varieties (Mountain Magic, Iron Lady, Defiant F1) — resistance significantly reduces but does not eliminate susceptibility. Never work in the garden when foliage is wet. Avoid planting near potato crops. Apply preventive copper or chlorothalonil sprays when cool, wet weather is forecast.",
            sevenDayPlan = listOf(
                "Day 1: Assess spread immediately. If lesions cover more than 3–4 leaves or any stem is affected, bag and destroy the plant same-day. Late blight can spread to every neighboring plant within 3–5 days.",
                "Day 2: If caught very early (single small lesion), remove the infected leaf only, then apply chlorothalonil or copper fungicide immediately to the entire plant and all surrounding tomatoes.",
                "Day 3: Inspect every tomato AND potato plant within the garden or nearby plots — Phytophthora infestans infects both equally. Separate outbreaks are often linked.",
                "Day 4: Maximize airflow and sun exposure around all remaining plants. The pathogen requires cool, damp, stagnant air — reducing canopy density slows spread.",
                "Day 5: Apply a protective copper or chlorothalonil spray to all surrounding healthy tomato plants. Treat this as an insurance spray even if they show no symptoms.",
                "Day 6: Stop all overhead irrigation permanently. Keep canopy humidity as low as possible — do not water in the evening.",
                "Day 7: Shift from weekly to daily re-inspection for the next 2 weeks. Late blight moves faster than any other tomato disease. Re-spray every 5–7 days during ongoing cool, wet weather."
            )
        ),

        "Tomato_Leaf_Mold" to DiseaseResult(
            diseaseName = "Leaf Mold (Tomato)",
            description = "Pale green to yellow spots develop on the upper leaf surface, with a corresponding patch of olive-green to gray-purple fuzzy sporulation on the underside directly below. Affected leaves curl, dry out, and drop. In enclosed growing spaces (greenhouses, tunnels, high-humidity plots), the disease can spread rapidly from a few spots to complete defoliation of the lower canopy within weeks.",
            causes = "Caused by Passalora fulva (formerly Fulvia fulva) fungus, which requires sustained relative humidity above 85% to sporulate and spread. It is primarily a disease of enclosed or sheltered growing environments — open-field cases are rare unless in naturally humid microclimates. The fungus does not produce spores at low humidity even if the plants are already infected.",
            treatment = "Improving ventilation and reducing humidity is more important than any chemical treatment for this disease. If humidity cannot be reduced, apply chlorothalonil or a copper-based fungicide, focused on leaf undersides where sporulation occurs. Water only in the early morning so foliage dries by early afternoon. If growing in a greenhouse, open vents fully, add circulation fans, and consider reducing plant density.",
            preventionTips = "Maintain airflow through the canopy by pruning lower leaves and side-shoots. In enclosed spaces, use fans to keep humidity below 85%. Plant at wider spacing than the minimum recommended when growing in high-humidity environments. Use resistant tomato varieties where available.",
            sevenDayPlan = listOf(
                "Day 1: Immediately improve air circulation — open all vents, add a fan, or remove nearby structures blocking airflow. Ventilation control is the most critical single action for this disease.",
                "Day 2: Apply chlorothalonil or copper-based fungicide, focused specifically on the undersides of leaves where the olive-gray mold develops and sporulates.",
                "Day 3: Reduce humidity further by watering in the early morning only, ensuring all foliage is completely dry before nightfall. Evening dew is the key driver of overnight sporulation.",
                "Day 4: Inspect the undersides of all leaves at mid-canopy height for surviving olive-colored sporulation patches — this is where active disease sits even if upper surfaces look clear.",
                "Day 5: Prune all leaves with active mold sporulation on the underside. Remove pruned material from the growing area immediately.",
                "Day 6: Monitor ambient humidity if a gauge is available. If relative humidity consistently exceeds 85% in the canopy, structural ventilation changes (not just spraying) are required.",
                "Day 7: Reassess — leaf mold typically clears once humidity is controlled below 85%, even without repeat spraying. If humidity remains high, continue 7-day fungicide rotations until conditions improve."
            )
        ),

        "Tomato_Septoria_leaf_spot" to DiseaseResult(
            diseaseName = "Septoria Leaf Spot (Tomato)",
            description = "Numerous small (3–6 mm), circular spots with dark brown borders, gray-to-tan centers, and tiny black specks (pycnidia — fungal fruiting bodies) visible at the center appear first on the oldest lower leaves. Heavy infection causes rapid yellowing and complete defoliation of the lower plant, moving upward progressively. Unlike early blight, Septoria rarely attacks fruit but the defoliation it causes weakens the plant severely over the season.",
            causes = "Caused by Septoria lycopersici fungus, which overwinters in infected crop debris and volunteer solanaceous plants in the soil. Splash dispersal during rain or irrigation moves spores from soil debris onto lower leaves. Wet foliage for extended periods at 20–25°C is optimal for infection. Spores spread readily by contact — on hands, tools, and clothing — making sanitation critical.",
            treatment = "Remove diseased lower leaves at first sign. Apply chlorothalonil or mancozeb as contact fungicides on a strict 7–10 day cycle — these are the most recommended products for Septoria. Copper fungicide is an effective organic alternative. Mulch the base heavily to prevent spore splash — this is the single most impactful cultural control step. Rotate fungicide groups (chlorothalonil → copper → mancozeb) to prevent resistance. Septoria can be suppressed and contained with early treatment but not eliminated from an infected plant.",
            preventionTips = "Mulch plant bases with 5–8 cm of straw or plastic film from planting onward. Practice 2-year minimum crop rotation away from tomato. Never work among plants when foliage is wet — Septoria spores spread easily on wet hands and clothing. Sanitize hands and tools frequently during garden work.",
            sevenDayPlan = listOf(
                "Day 1: Remove all spotted lower leaves, placing directly into a bag without shaking. Septoria spores release and travel easily from disturbed infected tissue.",
                "Day 2: Apply chlorothalonil or mancozeb fungicide as a thorough spray covering both surfaces of all remaining leaves.",
                "Day 3: Lay 5–8 cm of straw mulch around the base to create a physical barrier between soil-borne spores and lower leaves — one of the highest-impact single actions for Septoria.",
                "Day 4: Keep all foliage dry for the entire treatment week. Water only at soil level.",
                "Day 5: Wash hands thoroughly and disinfect tools after handling any plant in the garden — Septoria spreads easily by contact and can jump from plant to plant on your hands.",
                "Day 6: Check whether spots are advancing to upper leaves. If yes, the spray interval should be shortened to 5–7 days.",
                "Day 7: Re-apply fungicide on the 7–10 day schedule, rotating to a different active (e.g. copper if chlorothalonil was used on Day 2). Continue rotations through the rest of the season."
            )
        ),

        "Tomato_Spider_mites_Two_spotted_spider_mite" to DiseaseResult(
            diseaseName = "Spider Mites (Tomato)",
            description = "Fine yellow stippling (tiny white or yellow dots) appears across the leaf surface as mites pierce individual cells to feed. Fine silky webbing forms on leaf undersides and between leaf axils and stems as infestation increases. Leaves turn bronze or papery and may dry out completely as feeding damage accumulates. Spider mite populations can double within 3–5 days in hot, dry conditions, making early action critical.",
            causes = "Caused by the two-spotted spider mite (Tetranychus urticae), an arachnid pest (not an insect) that thrives in hot (above 27°C), dry, dusty conditions. Plants under drought stress are significantly more attractive to mites and suffer faster damage. Broad-spectrum insecticide overuse kills natural mite predators (predatory mites, ladybugs), causing population flare-ups. Webbing on the leaf underside signals a heavy infestation — mites protected under webbing are significantly harder to reach with miticides.",
            treatment = "Start with a strong water jet to physically dislodge mites and webbing. For light to moderate infestation, apply insecticidal soap or horticultural oil directly to leaf undersides (this is where mites live, not on top). These must contact the mites to work and have no residual — repeat every 3–5 days. For heavy infestations (visible webbing, bronze leaves), escalate to a true miticide: bifenazate (Acramite) or abamectin (Agri-Mek) are the most research-proven and provide up to 21-day residual. General insecticides do not kill mites and may worsen outbreaks by eliminating predatory mites. Rotate miticide classes to prevent resistance.",
            preventionTips = "Keep plants well-watered — drought stress makes plants dramatically more attractive to mites. Periodically hose down foliage in hot, dry weather. Avoid broad-spectrum insecticides that eliminate natural mite predators. Encourage predatory mites (Phytoseiulus persimilis) in the growing environment.",
            sevenDayPlan = listOf(
                "Day 1: Spray the entire plant with a strong jet of water, focusing directly on leaf undersides. Physically knocking mites and webbing off is the first line of action — it immediately reduces the population without any chemical.",
                "Day 2: Apply insecticidal soap or horticultural oil (neem oil) to all leaf undersides immediately after the water rinse. Mites live on the underside — spraying the top does nothing. Ensure complete wet coverage.",
                "Day 3: Water the soil deeply. Drought-stressed plants are 3–5× more attractive to mites. Adequate root-zone moisture is a key management tool alongside chemical treatment.",
                "Day 4: Re-inspect leaf undersides for new webbing and live mites. Mite eggs hatch in 3–5 days, so early re-infestation is expected and normal — do not assume one treatment worked.",
                "Day 5: If webbing persists or has spread after soap/oil, escalate immediately to bifenazate (Acramite) or abamectin (Agri-Mek) miticide. Apply to all leaf undersides with thorough coverage — webbing reduces chemical penetration so break through it with the spray stream.",
                "Day 6: Maintain higher humidity around the plant base. Spider mites strongly prefer low-humidity, dusty, hot conditions — periodic light misting around (not on) the canopy makes the microclimate less favorable.",
                "Day 7: Re-inspect closely. Mite populations can rebuild within a week from surviving eggs. Plan at minimum two full treatment rounds 5–7 days apart before considering the outbreak controlled."
            )
        ),

        "Tomato__Target_Spot" to DiseaseResult(
            diseaseName = "Target Spot (Tomato)",
            description = "Dark brown lesions with concentric rings resembling a target or bullseye pattern develop first in the dense lower canopy where humidity is highest, then move upward. Sunken, similarly-ringed lesions can form on fruit in severe cases. Unlike early blight (which also shows rings), target spot lesions are typically larger with less defined yellow halos and are strongly associated with dense, poorly-ventilated canopies.",
            causes = "Caused by Corynespora cassiicola fungus, which thrives when warm temperatures (25–30°C) combine with high relative humidity and a dense, closed canopy that traps moisture around leaves. The disease builds up from infected crop debris and soil and is significantly worsened by overhead irrigation.",
            treatment = "Apply azoxystrobin (systemic, Group 11) for curative action on active lesions, or chlorothalonil (contact, Group M5) as a protectant. Tank-mixing both covers both modes of action simultaneously. Rotate FRAC groups between applications to manage resistance — do not apply more than 2 consecutive Group 11 applications. Equally important: prune and open the canopy to improve airflow, which addresses the microclimate conditions the disease requires. Fruit with lesions confirms advanced infection and should be removed.",
            preventionTips = "Prune lower leaves and side-shoots to maintain an open canopy structure. Destroy crop debris at the end of every season — this disease overwinters in field residue. Practice crop rotation. Use drip irrigation rather than overhead sprinklers.",
            sevenDayPlan = listOf(
                "Day 1: Remove all infected leaves and any fruit showing sunken lesions. Fruit lesions indicate the infection is advanced — remove them to prevent further spore production.",
                "Day 2: Apply azoxystrobin or chlorothalonil fungicide as a thorough full-plant spray. If both are available, a tank mix covers systemic and contact protection simultaneously.",
                "Day 3: Stake or tie all plant stems securely. Keeping foliage elevated and supported off nearby surfaces improves drying speed and reduces splash spread.",
                "Day 4: Water only at the soil base in the early morning. Foliage should be completely dry by noon each day.",
                "Day 5: Prune lower branches and dense interior growth to open the canopy. Improved airflow is as important as fungicide — the disease cannot sustain itself below 85% relative humidity.",
                "Day 6: Monitor fruit clusters closely for new lesions. Target spot on fruit means the spray program is not yet achieving full control.",
                "Day 7: Re-apply fungicide on a 10–14 day rotation if humid weather continues. If azoxystrobin was used on Day 2, switch to chlorothalonil this application to prevent Group 11 resistance."
            )
        ),

        "Tomato__Tomato_YellowLeaf__Curl_Virus" to DiseaseResult(
            diseaseName = "Yellow Leaf Curl Virus (Tomato)",
            description = "Leaves curl sharply upward, become smaller, show pronounced yellowing especially along margins, and new internodes shorten — giving the plant a compact, stunted, bushy appearance. Flowering drops sharply and fruit set fails nearly completely in plants infected while young. Older infected plants show reduced yield and deformed fruit. This is one of the most economically devastating viral diseases on tomato worldwide.",
            causes = "Caused by Tomato Yellow Leaf Curl Virus (TYLCV), a begomovirus transmitted exclusively by the silverleaf whitefly (Bemisia tabaci). The virus cannot spread through soil, seed, water, or human contact. Whiteflies transmit the virus within minutes of feeding on an infected plant and can then infect healthy plants for several days afterward. Warm, dry conditions favor rapid whitefly population growth and virus spread.",
            treatment = "No chemical cures TYLCV once a plant is infected. All management targets the whitefly vector. Remove severely infected plants immediately — they are permanent virus and whitefly reservoirs. For whitefly suppression: neem oil and insecticidal soap for organic control; acetamiprid, thiamethoxam, or spiromesifen for chemical control. Rotate insecticide classes every 2 sprays — Bemisia tabaci develops resistance rapidly, particularly to imidacloprid and pyrethroids. Reflective silver mulch laid before transplanting is one of the most effective documented tools for delaying infection. Yellow sticky traps monitor population pressure. Resistant tomato varieties (TYLCV-resistant hybrids) are the most reliable long-term tool where available.",
            preventionTips = "Install reflective silver mulch before transplanting. Place yellow sticky traps at canopy height for monitoring. Plant TYLCV-resistant varieties where available. Control weeds within 2 meters — they are whitefly overwintering reservoirs. Inspect transplants and reject any showing curl symptoms before planting.",
            sevenDayPlan = listOf(
                "Day 1: Inspect all leaf undersides for silverleaf whitefly adults (tiny white insects) or nymphs (flat, scale-like, pale green). If confirmed and curling is severe, bag and remove the plant immediately.",
                "Day 2: Dispose of the plant in the trash away from the garden. Never compost virus-infected material — the virus can persist and whiteflies may continue feeding on composting material.",
                "Day 3: Spray neem oil or insecticidal soap on all remaining healthy tomato plants, targeting leaf undersides. This reduces the whitefly population that could spread virus from the removed plant to others.",
                "Day 4: Install yellow sticky traps at canopy height around the entire planting area. Count trapped insects daily — rising counts mean active whitefly movement is ongoing.",
                "Day 5: If trap counts remain high after neem oil application, escalate to acetamiprid or thiamethoxam systemic insecticide. Avoid imidacloprid and pyrethroid-only programs — Bemisia commonly shows resistance to these.",
                "Day 6: Remove all weeds and volunteer plants within 2 meters of the tomato bed. Weeds and wild solanaceous plants are primary overwintering hosts for both whiteflies and TYLCV.",
                "Day 7: Continue daily monitoring of traps. Consider planting resistant TYLCV varieties for the next crop cycle — genetic resistance is the most reliable and sustainable long-term control."
            )
        ),

        "Tomato__Tomato_mosaic_virus" to DiseaseResult(
            diseaseName = "Mosaic Virus (Tomato)",
            description = "Foliage develops a mottled mosaic of light and dark green patches, often with distorted, fern-like or thread-like leaflets (a symptom called 'filiforming'). Plant growth is stunted, leaf edges may curl, and both fruit yield and quality decline significantly. Unlike TYLCV, TMV-infected plants rarely die outright but become unproductive.",
            causes = "Tomato Mosaic Virus (ToMV) and Tobacco Mosaic Virus (TMV) are among the most mechanically contagious plant viruses known. They spread through direct contact via contaminated hands, tools, stakes, clothing, and infected seed — not through insects. TMV notably survives in processed tobacco products and can be introduced by smokers handling plants without washing hands. The virus can remain infectious on dry surfaces and tools for months to years.",
            treatment = "No chemical product cures a virus-infected plant. Remove and destroy infected plants immediately to stop mechanical spread to the rest of the crop. Sterilize all stakes, cages, ties, and tools with a 1:4 bleach-to-water solution. Wash hands thoroughly with soap after handling any infected plant — and especially after handling tobacco products before gardening. Use TMV-resistant tomato varieties for future planting (varieties with the Tm-2 or Tm-22 gene provide resistance to common strains).",
            preventionTips = "Wash hands with soap before touching plants, especially after handling tobacco products. Disinfect all garden tools between uses with bleach solution or 70% alcohol. Use certified TMV-free seed or treat seed with trisodium phosphate (TSP) solution. Plant resistant varieties with confirmed Tm-2/Tm-22 resistance gene.",
            sevenDayPlan = listOf(
                "Day 1: Remove the infected plant immediately by pulling from the root. Do not shake the plant — physical disturbance releases virus particles onto nearby surfaces and hands.",
                "Day 2: Bag the removed plant in heavy plastic and dispose of in the trash — never compost TMV-infected material. TMV remains infectious on dead plant tissue for extended periods.",
                "Day 3: Sterilize every item that contacted the infected plant: stakes, cages, ties, pruning shears, and any support structures. Use a 10% bleach solution (1 part bleach, 9 parts water) and allow to air-dry completely.",
                "Day 4: Wash hands thoroughly with soap and water before touching any other plants in the garden. Remind all people working in the garden about TMV hand-transmission risk, especially smokers.",
                "Day 5: Closely inspect all neighboring tomato plants for early mottling or leaf distortion. TMV symptoms can take 7–14 days to appear after transmission, so early inspections may not yet show results.",
                "Day 6: Control aphids and other sap-sucking insects on remaining plants — while not primary vectors for TMV, they create wounds that allow mechanical virus entry.",
                "Day 7: Continue weekly inspections of the full garden for the next 3 weeks. Plan for TMV-resistant varieties (with Tm-2 gene) for the next crop cycle to prevent recurrence."
            )
        ),

        "Tomato_healthy" to DiseaseResult(
            diseaseName = "Healthy Tomato Plant",
            description = "Leaves are uniformly deep green without spots, mold, curling, stippling, or discoloration. Stems are upright and sturdy. Growth is progressing normally with active flowering and fruit set, and fruit develops without lesions, cracks, or deformity.",
            causes = "The result of consistently good cultural practice: correct plant spacing for airflow, soil-level irrigation, balanced nutrition, 6+ hours of direct sun, adequate support (staking/caging), and regular proactive scouting that catches problems before they establish.",
            treatment = "No active disease is present. The focus is a proactive maintenance routine that keeps the plant vigorous and resistant: consistent watering schedule, regular balanced feeding, structural support, and lower canopy management for airflow.",
            preventionTips = "Continue regular base watering and bi-weekly fertilization. Remove non-productive lower branches to maintain airflow. Scout leaf undersides weekly for whiteflies, mites, and early bacterial or fungal spotting even when the plant appears completely healthy.",
            sevenDayPlan = listOf(
                "Day 1: Water deeply at soil level and inspect all leaves  both surfaces — for early spots, stippling, or pest presence. The goal is catching anything at the earliest possible stage.",
                "Day 2: Prune any non-productive lower branches and leaves touching or near the soil. This maintains the clean air gap at the base and removes the primary entry routes for soil-borne disease.",
                "Day 3: Check staking and caging. Secure any leaning stems before fruit weight increases — unsupported stems increase disease risk from soil contact and reduce airflow.",
                "Day 4: Apply a balanced NPK fertilizer if the last feeding was more than 2 weeks ago. Consistent nutrition is the plant's primary defense against stress-induced susceptibility.",
                "Day 5: Water again according to the established schedule, adjusting for any rainfall received during the week.",
                "Day 6: Scout specifically for early mite stippling on older leaves, whitefly colonies on undersides, and aphid clusters on new growth. Weekly scouting catches these before they reach treatment thresholds.",
                "Day 7: Full plant inspection from soil level to growing tips. No new symptoms? Continue the same maintenance routine. Any new finding? Begin targeted treatment immediately — early-stage treatment is always more effective and cheaper than reactive response."
            )
        ),


        // Unrecognized / Low Confidence Fallback
        "Unrecognized" to DiseaseResult(
            diseaseName = "Image Unrecognized (Low Confidence)",
            description = "The AI model cannot confidently identify the plant or disease. Confidence is below 60%.",
            causes = "The plant or disease may not be in the system's database, or the image conditions are not optimal.",
            treatment = "Try capturing the photo again with the leaf clearly visible and well-lit.",
            preventionTips = "Position the leaf in the center of the frame with good lighting, and focus before capturing.",
            sevenDayPlan = listOf(
                "Action 1: Position the leaf in the center of the frame.",
                "Action 2: Make sure there is good natural lighting, avoiding harsh shadows.",
                "Action 3: Tap the screen to focus before capturing.",
                "Action 4: If the app still fails, the plant condition may not be supported by the current AI model."
            )
        )
    )

    private val unrecognizedResult = DiseaseResult(
        diseaseName = "Image Unrecognized (Low Confidence)",
        description = "The AI model cannot confidently identify the plant or disease. Confidence is below 60%.",
        causes = "The plant or disease may not be in the system's database, or the image conditions are not optimal.",
        treatment = "Try capturing the photo again with the leaf clearly visible and well-lit.",
        preventionTips = "Position the leaf in the center of the frame with good lighting, and focus before capturing.",
        sevenDayPlan = listOf(
            "Action 1: Clean your phone camera lens.",
            "Action 2: Position the leaf in the center of the frame.",
            "Action 3: Make sure there is good natural lighting, avoiding harsh shadows.",
            "Action 4: Tap the screen to focus before capturing.",
            "Action 5: If the app still fails, the plant condition may not be supported by the current AI model."
        )
    )

    fun getDiseaseInfo(label: String?): DiseaseResult {
        if (label.isNullOrBlank() || label == "Unknown") {
            return unrecognizedResult
        }

        return database[label] ?: unrecognizedResult
    }
}
