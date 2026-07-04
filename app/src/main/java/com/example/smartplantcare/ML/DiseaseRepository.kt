package com.example.smartplantcare.ML

import com.example.smartplantcare.data.DiseaseResult

object DiseaseRepository {

    private val database: Map<String, DiseaseResult> = mapOf(

        // ==========================================
        // CHILI DISEASES
        // ==========================================
        "Chili_Bacterial_Spot" to DiseaseResult(
            diseaseName = "Bacterial Spot (Chili)",
            description = "Water-soaked spots on leaves that turn brown/black, causing leaves to drop.",
            causes = "Xanthomonas bacteria spreading through water splashes.",
            treatment = "Apply copper-based bactericide. Remove severely infected leaves.",
            preventionTips = "Avoid overhead watering. Space plants for good air circulation.",
            sevenDayPlan = listOf(
                "Day 1: Isolate the plant and prune heavily spotted leaves. Disinfect tools.",
                "Day 2: Apply a copper-based bactericide spray early in the morning.",
                "Day 3: Withhold watering to let the topsoil dry out slightly.",
                "Day 4: Check if spots are spreading to new growth.",
                "Day 5: Re-apply bactericide if heavy rain washed away the first application.",
                "Day 6: Clear fallen debris around the plant base.",
                "Day 7: Evaluate plant status; if stable, resume bottom-watering."
            )
        ),
        "Chili_Cercospora_Leaf_Spot" to DiseaseResult(
            diseaseName = "Cercospora Leaf Spot (Chili)",
            description = "Circular spots with light gray centers and dark margins.",
            causes = "Cercospora fungus thriving in prolonged wetness.",
            treatment = "Use fungicidal sprays containing chlorothalonil.",
            preventionTips = "Ensure proper drainage and practice crop rotation.",
            sevenDayPlan = listOf(
                "Day 1: Pluck off leaves showing frog-eye spots and dispose of them.",
                "Day 2: Spray a broad-spectrum fungicide.",
                "Day 3: Improve airflow around the plant by trimming adjacent dense foliage.",
                "Day 4: Monitor for new spot formation.",
                "Day 5: Water the plant strictly at the base.",
                "Day 6: Apply a light foliar fertilizer to boost plant immunity.",
                "Day 7: Re-apply fungicide as per product instructions."
            )
        ),
        "Chili_Curl_Virus" to DiseaseResult(
            diseaseName = "Leaf Curl Virus (Chili)",
            description = "Leaves curl upward, become stunted, and turn yellowish.",
            causes = "Begomovirus transmitted primarily by whiteflies.",
            treatment = "No cure for the virus. Control the insect vectors (whiteflies).",
            preventionTips = "Use insecticidal soap and reflective mulches to deter whiteflies.",
            sevenDayPlan = listOf(
                "Day 1: Check under leaves for whiteflies. If found, apply neem oil or insecticidal soap.",
                "Day 2: Uproot the plant entirely if the infection is severe to save nearby plants.",
                "Day 3: Set up yellow sticky traps around the garden.",
                "Day 4: Re-spray neem oil on surrounding healthy plants as a preventative measure.",
                "Day 5: Monitor traps for whitefly population.",
                "Day 6: Ensure weeds around the area are cleared (they host the virus).",
                "Day 7: Continue pest control monitoring."
            )
        ),
        "Chili_Healthy_Leaf" to DiseaseResult(
            diseaseName = "Healthy Chili Plant",
            description = "The plant shows no signs of visible disease or severe pest infestation.",
            causes = "Good agricultural practices.",
            treatment = "None needed.",
            preventionTips = "Maintain consistent watering and fertilization.",
            sevenDayPlan = listOf(
                "Day 1-7: Maintain regular watering schedule, ensure good sunlight, and inspect weekly for pests."
            )
        ),
        "Chili_Nutrition_Deficiency" to DiseaseResult(
            diseaseName = "Nutrient Deficiency (Chili)",
            description = "Yellowing of leaves, stunted growth, or purple tints due to lack of NPK or micronutrients.",
            causes = "Poor soil quality or incorrect pH preventing nutrient absorption.",
            treatment = "Apply a balanced NPK liquid fertilizer or specific micronutrients.",
            preventionTips = "Amend soil with compost before planting and check soil pH.",
            sevenDayPlan = listOf(
                "Day 1: Apply a fast-acting liquid fertilizer (high nitrogen if leaves are pale yellow).",
                "Day 2: Check soil moisture; ensure it's not waterlogged (which stops nutrient uptake).",
                "Day 3: Observe the plant. (Nutrient recovery takes a few days).",
                "Day 4: Add compost or organic matter to the topsoil.",
                "Day 5: Monitor new leaf growth for healthy green color.",
                "Day 6: Avoid over-fertilizing to prevent root burn.",
                "Day 7: Plan a regular bi-weekly feeding schedule."
            )
        ),
        "Chili_White_Spot" to DiseaseResult(
            diseaseName = "White Spot (Chili)",
            description = "Small white powdery or raised spots on leaves.",
            causes = "Fungal infection or pest damage.",
            treatment = "Use sulfur-based fungicides or neem oil depending on the exact cause.",
            preventionTips = "Keep foliage dry and ensure good air circulation.",
            sevenDayPlan = listOf(
                "Day 1: Wash the leaves gently with water and insecticidal soap.",
                "Day 2: Apply a neem oil solution at dusk.",
                "Day 3: Observe if the white spots are spreading.",
                "Day 4: Prune heavily affected areas.",
                "Day 5: Apply a sulfur-based organic fungicide if spots persist.",
                "Day 6: Let the soil dry slightly.",
                "Day 7: Evaluate plant recovery."
            )
        ),


        "Eggplant_Cercospora Leaf Spot" to DiseaseResult(
            diseaseName = "Cercospora Leaf Spot (Eggplant)",
            description = "Round to irregular spots with gray centers on eggplant leaves.",
            causes = "Cercospora fungus.",
            treatment = "Apply appropriate fungicide and remove infected debris.",
            preventionTips = "Water at the base, avoid wetting leaves.",
            sevenDayPlan = listOf(
                "Day 1: Remove all leaves with spots. Do not compost them.",
                "Day 2: Spray a copper or chlorothalonil fungicide.",
                "Day 3: Spread mulch at the base to prevent soil splashing.",
                "Day 4: Monitor plant for new spots.",
                "Day 5: Provide a balanced fertilizer to help the plant recover lost foliage.",
                "Day 6: Check drainage; ensure roots aren't sitting in water.",
                "Day 7: Reapply fungicide if weather has been rainy."
            )
        ),
        "Eggplant_Healthy Leaf" to DiseaseResult(
            diseaseName = "Healthy Eggplant",
            description = "Plant is vibrant green with no visible spots or curling.",
            causes = "Optimal care.",
            treatment = "None.",
            preventionTips = "Keep up the good work.",
            sevenDayPlan = listOf(
                "Day 1-7: Continue deep watering twice a week and inspect under leaves for early pest signs."
            )
        ),
        "Eggplant_Insect Pest Disease" to DiseaseResult(
            diseaseName = "Insect Pest Infestation (Eggplant)",
            description = "Visible bite marks, holes, or insects (like flea beetles or aphids) on the plant.",
            causes = "Various garden pests targeting eggplants.",
            treatment = "Use insecticidal soap, neem oil, or manual removal.",
            preventionTips = "Use row covers for young plants and encourage beneficial insects like ladybugs.",
            sevenDayPlan = listOf(
                "Day 1: Physically remove large pests and spray neem oil or soapy water.",
                "Day 2: Check under leaves for eggs and crush them.",
                "Day 3: Re-apply neem oil if the infestation is heavy.",
                "Day 4: Clear weeds around the plant that harbor pests.",
                "Day 5: Check plant for stress and water adequately.",
                "Day 6: Introduce sticky traps for flying insects.",
                "Day 7: Do a thorough inspection to ensure pest life cycle is broken."
            )
        ),
        "Eggplant_Leaf Spot Disease" to DiseaseResult(
            diseaseName = "General Leaf Spot (Eggplant)",
            description = "Irregular brown or black spots, often causing leaf yellowing.",
            causes = "Fungal or bacterial pathogens.",
            treatment = "Remove infected leaves and apply a broad-spectrum fungicide.",
            preventionTips = "Improve air flow by spacing plants properly.",
            sevenDayPlan = listOf(
                "Day 1: Prune infected parts and clean tools.",
                "Day 2: Apply fungicide during the cooler part of the day.",
                "Day 3: Water only the soil, keeping the foliage entirely dry.",
                "Day 4: Monitor the spread to healthy leaves.",
                "Day 5: Apply a second round of treatment if recommended by the product.",
                "Day 6: Ensure the plant gets full sun to keep leaves dry.",
                "Day 7: Assess overall plant health."
            )
        ),
        "Eggplant_Mosaic Virus Disease" to DiseaseResult(
            diseaseName = "Mosaic Virus (Eggplant)",
            description = "Mottled yellow and green patterns on leaves, stunted growth.",
            causes = "Viral infection spread by aphids or contaminated tools.",
            treatment = "No cure. Infected plants must be destroyed.",
            preventionTips = "Control aphid populations and disinfect tools.",
            sevenDayPlan = listOf(
                "Day 1: Immediately remove the infected plant to protect the garden.",
                "Day 2: Destroy the plant (burn or bag it, do not compost).",
                "Day 3: Treat surrounding healthy plants with neem oil to kill aphid vectors.",
                "Day 4: Sanitize all garden tools with bleach or alcohol.",
                "Day 5: Check neighboring plants for mottling.",
                "Day 6: Keep the area weed-free.",
                "Day 7: Re-evaluate the garden for any remaining pests."
            )
        ),
        "Eggplant_Powdery Mildew" to DiseaseResult(
            diseaseName = "Powdery Mildew (Eggplant)",
            description = "White, powdery fungal patches on the upper surfaces of leaves.",
            causes = "Podosphaera fungus, thriving in warm, dry climates with high humidity.",
            treatment = "Apply sulfur fungicides or a baking soda spray mixture.",
            preventionTips = "Ensure good sun exposure and air circulation.",
            sevenDayPlan = listOf(
                "Day 1: Spray leaves thoroughly with a baking soda and water mixture or sulfur spray.",
                "Day 2: Trim away the most severely covered leaves.",
                "Day 3: Ensure the plant is getting adequate direct sunlight.",
                "Day 4: Check if the white powder is subsiding.",
                "Day 5: Water the plant adequately at the base to reduce drought stress.",
                "Day 6: Reapply the spray to catch surviving fungal spores.",
                "Day 7: Monitor new growth for signs of infection."
            )
        ),


        "Tomato_Bacterial_spot" to DiseaseResult(
            diseaseName = "Bacterial Spot (Tomato)",
            description = "Small, dark, greasy spots on leaves and scabs on fruit.",
            causes = "Xanthomonas bacteria.",
            treatment = "Copper fungicide sprays can slow the spread.",
            preventionTips = "Avoid overhead irrigation and do not work with plants when wet.",
            sevenDayPlan = listOf(
                "Day 1: Prune infected foliage and avoid touching healthy plants afterward.",
                "Day 2: Apply a copper fungicide spray.",
                "Day 3: Hold off on watering to let the environment dry out.",
                "Day 4: Monitor the fruit for scab formation.",
                "Day 5: Re-spray if heavy rain occurred.",
                "Day 6: Feed with a low-nitrogen fertilizer to prevent excessive soft leaf growth.",
                "Day 7: Assess the plant to see if the spread has halted."
            )
        ),
        "Tomato_Early_blight" to DiseaseResult(
            diseaseName = "Early Blight (Tomato)",
            description = "Bullseye-patterned brown spots usually starting on lower leaves.",
            causes = "Alternaria solani fungus.",
            treatment = "Remove lower leaves and apply organic fungicides.",
            preventionTips = "Mulch around the base to stop spores from splashing up from the dirt.",
            sevenDayPlan = listOf(
                "Day 1: Cut off all lower leaves touching the soil.",
                "Day 2: Apply a fungicide containing chlorothalonil or copper.",
                "Day 3: Lay down organic mulch (like straw) around the base.",
                "Day 4: Water at the soil level only.",
                "Day 5: Check upper leaves for new spots.",
                "Day 6: Provide a balanced fertilizer to support the plant.",
                "Day 7: Plan a routine fungicide schedule."
            )
        ),
        "Tomato_Late_blight" to DiseaseResult(
            diseaseName = "Late Blight (Tomato)",
            description = "Large, dark, water-soaked patches on leaves and stems; rotting fruit.",
            causes = "Phytophthora infestans (water mold). Highly contagious.",
            treatment = "Difficult to treat once severe. Immediate removal is often best.",
            preventionTips = "Plant resistant varieties and maintain strict garden hygiene.",
            sevenDayPlan = listOf(
                "Day 1: If severe, bag and destroy the plant immediately to save your crop.",
                "Day 2: If caught very early, aggressively prune and apply copper fungicide.",
                "Day 3: Inspect all nearby tomato and potato plants.",
                "Day 4: Ensure the area has maximum sunlight and air movement.",
                "Day 5: Reapply fungicide to surrounding healthy plants.",
                "Day 6: Avoid watering to keep humidity low.",
                "Day 7: Evaluate if the plant needs to be pulled out completely."
            )
        ),
        "Tomato_Leaf_Mold" to DiseaseResult(
            diseaseName = "Leaf Mold (Tomato)",
            description = "Pale green/yellow spots on top of leaves with olive-green mold underneath.",
            causes = "Passalora fulva fungus in high humidity environments (like greenhouses).",
            treatment = "Improve ventilation and apply fungicides.",
            preventionTips = "Prune lower leaves to increase airflow.",
            sevenDayPlan = listOf(
                "Day 1: Improve air circulation by thinning out dense foliage.",
                "Day 2: Treat with a calcium or copper-based fungicide.",
                "Day 3: Reduce humidity around the plant by watering less.",
                "Day 4: Check the undersides of leaves for mold survival.",
                "Day 5: Prune any remaining moldy leaves.",
                "Day 6: Monitor the temperature (fungus loves cool, humid conditions).",
                "Day 7: Assess overall recovery."
            )
        ),
        "Tomato_Septoria_leaf_spot" to DiseaseResult(
            diseaseName = "Septoria Leaf Spot (Tomato)",
            description = "Numerous small, circular spots with dark borders and gray centers on lower leaves.",
            causes = "Septoria lycopersici fungus from infected soil debris.",
            treatment = "Remove diseased leaves and use fungicidal sprays.",
            preventionTips = "Mulch the base and practice crop rotation.",
            sevenDayPlan = listOf(
                "Day 1: Pinch off severely spotted lower leaves.",
                "Day 2: Apply chlorothalonil or copper fungicide.",
                "Day 3: Apply a thick layer of mulch over the soil.",
                "Day 4: Keep the plant leaves completely dry during watering.",
                "Day 5: Wash hands and tools thoroughly.",
                "Day 6: Check for spots moving up the stem.",
                "Day 7: Re-spray fungicide following the label's schedule."
            )
        ),
        "Tomato_Spider_mites_Two_spotted_spider_mite" to DiseaseResult(
            diseaseName = "Spider Mites (Tomato)",
            description = "Tiny yellow speckling on leaves and fine webbing under the leaves.",
            causes = "Microscopic arachnids thriving in hot, dry conditions.",
            treatment = "Hose down the plant with water or use horticultural oils/neem oil.",
            preventionTips = "Keep plants well-watered to avoid the dry stress that attracts mites.",
            sevenDayPlan = listOf(
                "Day 1: Spray the entire plant with a strong jet of water to knock off mites.",
                "Day 2: Apply neem oil or insecticidal soap, focusing on the undersides of leaves.",
                "Day 3: Water the soil deeply.",
                "Day 4: Check for new webbing.",
                "Day 5: Re-apply neem oil to kill newly hatched mites.",
                "Day 6: Maintain higher humidity around the plant.",
                "Day 7: Monitor closely; mites reproduce rapidly."
            )
        ),
        "Tomato__Target_Spot" to DiseaseResult(
            diseaseName = "Target Spot (Tomato)",
            description = "Dark brown spots with concentric rings on leaves and sunken spots on fruit.",
            causes = "Corynespora cassiicola fungus.",
            treatment = "Apply fungicides and improve airflow.",
            preventionTips = "Destroy crop residue at the end of the season.",
            sevenDayPlan = listOf(
                "Day 1: Remove infected leaves and any spotted fruit.",
                "Day 2: Apply a broad-spectrum fungicide.",
                "Day 3: Ensure the plant is securely staked to keep it off the ground.",
                "Day 4: Water only at the base early in the morning.",
                "Day 5: Trim surrounding plants to let wind pass through.",
                "Day 6: Monitor for any further spread.",
                "Day 7: Reapply treatment if necessary."
            )
        ),
        "Tomato__Tomato_YellowLeaf__Curl_Virus" to DiseaseResult(
            diseaseName = "Yellow Leaf Curl Virus (Tomato)",
            description = "Leaves curl upward, become yellowed at the edges, and growth is severely stunted.",
            causes = "Virus transmitted by the silverleaf whitefly.",
            treatment = "No cure. Infected plants must be destroyed.",
            preventionTips = "Control whitefly populations and use reflective mulch.",
            sevenDayPlan = listOf(
                "Day 1: Inspect for whiteflies. If confirmed, bag and uproot the plant.",
                "Day 2: Destroy the plant far from your garden.",
                "Day 3: Spray remaining healthy tomatoes with neem oil.",
                "Day 4: Place yellow sticky traps around the garden.",
                "Day 5: Monitor traps daily for whiteflies.",
                "Day 6: Clear all weed hosts nearby.",
                "Day 7: Continue whitefly prevention protocols."
            )
        ),
        "Tomato__Tomato_mosaic_virus" to DiseaseResult(
            diseaseName = "Mosaic Virus (Tomato)",
            description = "Light and dark green mottled foliage, stunted growth, and poor fruit yield.",
            causes = "Highly contagious virus spread by touch, seeds, or tobacco products.",
            treatment = "No cure. Remove and destroy the plant.",
            preventionTips = "Wash hands with soap after handling tobacco. Disinfect tools.",
            sevenDayPlan = listOf(
                "Day 1: Pull out the infected plant immediately.",
                "Day 2: Burn or throw away the plant (Do not compost!).",
                "Day 3: Sterilize all stakes, cages, and tools used on the plant with a 10% bleach solution.",
                "Day 4: Wash your hands thoroughly before touching other plants.",
                "Day 5: Inspect neighboring plants for mottling.",
                "Day 6: Ensure you are controlling aphids and other sucking insects.",
                "Day 7: Monitor the garden for the next two weeks."
            )
        ),
        "Tomato_healthy" to DiseaseResult(
            diseaseName = "Healthy Tomato Plant",
            description = "Leaves are uniform green without spots, molds, or curling.",
            causes = "Good health and management.",
            treatment = "None required.",
            preventionTips = "Continue providing steady water, fertilizer, and sunlight.",
            sevenDayPlan = listOf(
                "Day 1-7: Continue regular maintenance. Prune non-producing lower branches to maintain airflow."
            )
        )
    )


    private val unrecognizedResult = DiseaseResult(
        diseaseName = "Image Unrecognized (Low Confidence)",
        description = "The AI model cannot confidently identify the plant or disease. Confidence is below 60%.",
        causes = "The image might be blurry, too dark, out of focus, taken from a bad angle, or the plant is not in the system's database.",
        treatment = "Retake the photo. Ensure the leaf is clearly visible, well-lit, and fills the center of the camera frame.",
        preventionTips = "Wipe the camera lens, avoid taking photos directly against the sun, and focus on one specific leaf.",
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