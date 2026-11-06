package org.nsh07.wikireader.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class WikiApiCategoryResponse(
    val query: WikiCategoryQuery? = null,
    @SerialName("continue") val cont: WikiContinue? = null
)

// ── Article introductions (prop=extracts) ───────────────────────────────────
@Serializable
data class WikiApiIntroResponse(val query: WikiIntroQuery? = null)

@Serializable
data class WikiIntroQuery(val pages: List<WikiIntroPage> = emptyList())

@Serializable
data class WikiIntroPage(val title: String = "", val extract: String? = null)

// ── Picture sizes (prop=imageinfo) ───────────────────────────────────────────
@Serializable
data class WikiApiImageInfoResponse(val query: WikiImageInfoQuery? = null)

@Serializable
data class WikiImageInfoQuery(
    val pages: List<WikiImageInfoPage> = emptyList(),
    val normalized: List<WikiNormalizedTitle> = emptyList()
)

@Serializable
data class WikiNormalizedTitle(val from: String = "", val to: String = "")

@Serializable
data class WikiImageInfoPage(val title: String = "", val imageinfo: List<WikiImageInfoItem>? = null)

@Serializable
data class WikiImageInfoItem(val width: Int = 0, val height: Int = 0)

// ── Page views (prop=pageviews, PageViewInfo extension) ──────────────────────
@Serializable
data class WikiApiPageViewsResponse(val query: WikiPageViewsQuery? = null)

@Serializable
data class WikiPageViewsQuery(val pages: List<WikiPageViewsPage> = emptyList())

/** [pageviews] maps "yyyy-MM-dd" to that day's views (null = no data); the map itself is null if unavailable. */
@Serializable
data class WikiPageViewsPage(val title: String = "", val pageviews: Map<String, Int?>? = null)

// ── Category sizes (prop=categoryinfo) ───────────────────────────────────────
@Serializable
data class WikiApiCategoryInfoResponse(val query: WikiCategoryInfoQuery? = null)

@Serializable
data class WikiCategoryInfoQuery(val pages: List<WikiCategoryInfoPage> = emptyList())

@Serializable
data class WikiCategoryInfoPage(val title: String = "", val categoryinfo: WikiCategoryInfo? = null)

@Serializable
data class WikiCategoryInfo(val size: Int = 0, val pages: Int = 0, val subcats: Int = 0)

/** MediaWiki continuation block: pass both values back to fetch the next page of results. */
@Serializable
data class WikiContinue(
    val cmcontinue: String? = null,
    @SerialName("continue") val cont: String? = null
)

@Serializable
data class WikiCategoryQuery(val categorymembers: List<WikiCategoryMember> = emptyList())

@Serializable
data class WikiCategoryMember(
    val pageid: Int = 0,
    val ns: Int = 0,
    val title: String = "",
    /** "page" or "subcat" (only present when requested with cmprop=type). */
    val type: String = ""
)

/** Leaf selectable topic */
data class WikiTopic(val label: String, val category: String)

/** Middle level — has children chips underneath it */
data class WikiSubgroup(
    val label: String,
    val category: String,
    val children: List<WikiTopic> = emptyList()
)

/** Top level — expands to reveal subgroups (and/or direct leaf topics) */
data class WikiTopicGroup(
    val label: String,
    val category: String,
    val subgroups: List<WikiSubgroup> = emptyList()
)

val TOPIC_GROUPS: List<WikiTopicGroup> = listOf(

    // ── PLANTS ────────────────────────────────────────────────────────────────
    WikiTopicGroup("Plants", "Plants", listOf(
        WikiSubgroup("Flowering plants", "Flowering_plants", listOf(
            WikiTopic("Monocots", "Monocotyledons"),
            WikiTopic("Eudicots", "Eudicots"),
            WikiTopic("Rosids", "Rosids"),
            WikiTopic("Asterids", "Asterids"),
            WikiTopic("Orchids", "Orchids"),
            WikiTopic("Grasses", "Grasses"),
            WikiTopic("Legumes", "Legumes"),
            WikiTopic("Roses & relatives", "Rosaceae"),
            WikiTopic("Composites (daisies)", "Asteraceae"),
            WikiTopic("Nightshade family", "Solanaceae"),
            WikiTopic("Bromeliads", "Bromeliaceae"),
            WikiTopic("Lilies", "Liliaceae"),
        )),
        WikiSubgroup("Gymnosperms", "Gymnosperms", listOf(
            WikiTopic("Conifers", "Conifers"),
            WikiTopic("Cycads", "Cycads"),
            WikiTopic("Ginkgo", "Ginkgo"),
            WikiTopic("Gnetophytes", "Gnetophytes"),
        )),
        WikiSubgroup("Lower plants", "Embryophyte", listOf(
            WikiTopic("Ferns", "Ferns"),
            WikiTopic("Horsetails", "Equisetopsida"),
            WikiTopic("Mosses", "Mosses"),
            WikiTopic("Liverworts", "Liverworts"),
            WikiTopic("Hornworts", "Hornworts"),
        )),
        WikiSubgroup("Algae & fungi", "Algae", listOf(
            WikiTopic("Green algae", "Green_algae"),
            WikiTopic("Red algae", "Red_algae"),
            WikiTopic("Brown algae", "Brown_algae"),
            WikiTopic("Diatoms", "Diatoms"),
            WikiTopic("Lichens", "Lichens"),
            WikiTopic("Seaweeds", "Seaweed"),
        )),
        WikiSubgroup("Special & notable plants", "Carnivorous_plants", listOf(
            WikiTopic("Carnivorous plants", "Carnivorous_plants"),
            WikiTopic("Parasitic plants", "Parasitic_plants"),
            WikiTopic("Cacti", "Cactus"),
            WikiTopic("Succulents", "Succulents"),
            WikiTopic("Medicinal plants", "Medicinal_plants"),
            WikiTopic("Edible plants", "Edible_plants"),
            WikiTopic("Invasive plants", "Invasive_plants"),
        )),
        WikiSubgroup("Trees", "Trees", listOf(
            WikiTopic("Deciduous trees", "Deciduous_trees"),
            WikiTopic("Evergreen trees", "Evergreen_trees"),
            WikiTopic("Tropical trees", "Tropical_trees"),
            WikiTopic("Oaks", "Oaks"),
            WikiTopic("Pines", "Pines"),
            WikiTopic("Palms", "Arecaceae"),
        )),
    )),

    // ── BIOLOGY ───────────────────────────────────────────────────────────────
    WikiTopicGroup("Biology", "Biology", listOf(
        WikiSubgroup("Zoology", "Zoology", listOf(
            WikiTopic("Mammals", "Mammals"),
            WikiTopic("Birds", "Birds"),
            WikiTopic("Reptiles", "Reptiles"),
            WikiTopic("Amphibians", "Amphibians"),
            WikiTopic("Fish", "Fish"),
            WikiTopic("Insects", "Insects"),
            WikiTopic("Arachnids", "Arachnids"),
            WikiTopic("Molluscs", "Molluscs"),
            WikiTopic("Crustaceans", "Crustaceans"),
            WikiTopic("Worms", "Worms"),
        )),
        WikiSubgroup("Specific animal groups", "Animals", listOf(
            WikiTopic("Primates", "Primates"),
            WikiTopic("Cetaceans (whales)", "Cetaceans"),
            WikiTopic("Big cats", "Big_cats"),
            WikiTopic("Sharks", "Sharks"),
            WikiTopic("Dinosaurs", "Dinosaurs"),
            WikiTopic("Butterflies & moths", "Lepidoptera"),
            WikiTopic("Beetles", "Beetles"),
            WikiTopic("Ants & bees", "Hymenoptera"),
            WikiTopic("Corvids (crows)", "Corvidae"),
            WikiTopic("Parrots", "Parrots"),
        )),
        WikiSubgroup("Microbiology", "Microbiology", listOf(
            WikiTopic("Bacteria", "Bacteria"),
            WikiTopic("Viruses", "Viruses"),
            WikiTopic("Fungi", "Fungi"),
            WikiTopic("Protozoa", "Protozoa"),
            WikiTopic("Archaea", "Archaea"),
        )),
        WikiSubgroup("Genetics & molecular biology", "Genetics", listOf(
            WikiTopic("Molecular genetics", "Molecular_genetics"),
            WikiTopic("Population genetics", "Population_genetics"),
            WikiTopic("Genomics", "Genomics"),
            WikiTopic("Epigenetics", "Epigenetics"),
            WikiTopic("Gene editing", "Genome_editing"),
        )),
        WikiSubgroup("Ecology", "Ecology", listOf(
            WikiTopic("Ecosystems", "Ecosystems"),
            WikiTopic("Food webs", "Food_web"),
            WikiTopic("Biomes", "Biomes"),
            WikiTopic("Conservation biology", "Conservation_biology"),
            WikiTopic("Evolutionary ecology", "Evolutionary_ecology"),
        )),
        WikiSubgroup("Cell & molecular", "Cell_biology", listOf(
            WikiTopic("Cell biology", "Cell_biology"),
            WikiTopic("Biochemistry", "Biochemistry"),
            WikiTopic("Proteins", "Proteins"),
            WikiTopic("Enzymes", "Enzymes"),
            WikiTopic("Metabolism", "Metabolism"),
        )),
    )),

    // ── CHEMISTRY ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Chemistry", "Chemistry", listOf(
        WikiSubgroup("Organic chemistry", "Organic_chemistry", listOf(
            WikiTopic("Hydrocarbons", "Hydrocarbons"),
            WikiTopic("Aromatic compounds", "Aromatic_compounds"),
            WikiTopic("Alcohols & phenols", "Alcohols"),
            WikiTopic("Carboxylic acids", "Carboxylic_acids"),
            WikiTopic("Amines", "Amines"),
            WikiTopic("Heterocyclic compounds", "Heterocyclic_compounds"),
            WikiTopic("Natural products", "Natural_products_(chemistry)"),
            WikiTopic("Polymers", "Polymers"),
            WikiTopic("Stereochemistry", "Stereochemistry"),
            WikiTopic("Reaction mechanisms", "Organic_reaction"),
        )),
        WikiSubgroup("Inorganic chemistry", "Inorganic_chemistry", listOf(
            WikiTopic("Transition metals", "Transition_metals"),
            WikiTopic("Main group elements", "Main-group_element"),
            WikiTopic("Coordination chemistry", "Coordination_chemistry"),
            WikiTopic("Organometallics", "Organometallic_chemistry"),
            WikiTopic("Solid-state chemistry", "Solid-state_chemistry"),
        )),
        WikiSubgroup("Physical chemistry", "Physical_chemistry", listOf(
            WikiTopic("Thermochemistry", "Thermochemistry"),
            WikiTopic("Quantum chemistry", "Quantum_chemistry"),
            WikiTopic("Chemical kinetics", "Chemical_kinetics"),
            WikiTopic("Electrochemistry", "Electrochemistry"),
            WikiTopic("Spectroscopy", "Spectroscopy"),
            WikiTopic("Surface chemistry", "Surface_chemistry"),
        )),
        WikiSubgroup("Biochemistry", "Biochemistry", listOf(
            WikiTopic("Proteins & enzymes", "Enzyme"),
            WikiTopic("Nucleic acids", "Nucleic_acids"),
            WikiTopic("Lipids", "Lipids"),
            WikiTopic("Carbohydrates", "Carbohydrates"),
            WikiTopic("Metabolism", "Metabolism"),
            WikiTopic("Signal transduction", "Signal_transduction"),
        )),
        WikiSubgroup("Other branches", "Analytical_chemistry", listOf(
            WikiTopic("Analytical chemistry", "Analytical_chemistry"),
            WikiTopic("Nuclear chemistry", "Nuclear_chemistry"),
            WikiTopic("Green chemistry", "Green_chemistry"),
            WikiTopic("Polymer chemistry", "Polymer_chemistry"),
            WikiTopic("Computational chemistry", "Computational_chemistry"),
            WikiTopic("Chemical elements", "Chemical_elements"),
        )),
    )),

    // ── PHYSICS ───────────────────────────────────────────────────────────────
    WikiTopicGroup("Physics", "Physics", listOf(
        WikiSubgroup("Quantum & particle", "Quantum_mechanics", listOf(
            WikiTopic("Quantum mechanics", "Quantum_mechanics"),
            WikiTopic("Quantum field theory", "Quantum_field_theory"),
            WikiTopic("Particle physics", "Particle_physics"),
            WikiTopic("Nuclear physics", "Nuclear_physics"),
            WikiTopic("Atomic physics", "Atomic_physics"),
            WikiTopic("Standard Model", "Standard_Model"),
            WikiTopic("Antimatter", "Antimatter"),
        )),
        WikiSubgroup("Classical physics", "Classical_mechanics", listOf(
            WikiTopic("Classical mechanics", "Classical_mechanics"),
            WikiTopic("Thermodynamics", "Thermodynamics"),
            WikiTopic("Electromagnetism", "Electromagnetism"),
            WikiTopic("Optics", "Optics"),
            WikiTopic("Acoustics", "Acoustics"),
            WikiTopic("Fluid dynamics", "Fluid_dynamics"),
        )),
        WikiSubgroup("Modern & applied", "Condensed_matter_physics", listOf(
            WikiTopic("Relativity", "Theory_of_relativity"),
            WikiTopic("Condensed matter", "Condensed_matter_physics"),
            WikiTopic("Plasma physics", "Plasma_physics"),
            WikiTopic("Astrophysics", "Astrophysics"),
            WikiTopic("Biophysics", "Biophysics"),
            WikiTopic("Materials science", "Materials_science"),
            WikiTopic("Superconductivity", "Superconductivity"),
        )),
    )),

    // ── MATHEMATICS ───────────────────────────────────────────────────────────
    WikiTopicGroup("Mathematics", "Mathematics", listOf(
        WikiSubgroup("Pure mathematics", "Pure_mathematics", listOf(
            WikiTopic("Algebra", "Algebra"),
            WikiTopic("Abstract algebra", "Abstract_algebra"),
            WikiTopic("Linear algebra", "Linear_algebra"),
            WikiTopic("Number theory", "Number_theory"),
            WikiTopic("Topology", "Topology"),
            WikiTopic("Geometry", "Geometry"),
            WikiTopic("Differential geometry", "Differential_geometry"),
            WikiTopic("Mathematical analysis", "Mathematical_analysis"),
            WikiTopic("Complex analysis", "Complex_analysis"),
            WikiTopic("Set theory", "Set_theory"),
            WikiTopic("Mathematical logic", "Mathematical_logic"),
        )),
        WikiSubgroup("Applied mathematics", "Applied_mathematics", listOf(
            WikiTopic("Statistics", "Statistics"),
            WikiTopic("Probability", "Probability"),
            WikiTopic("Numerical analysis", "Numerical_analysis"),
            WikiTopic("Mathematical physics", "Mathematical_physics"),
            WikiTopic("Operations research", "Operations_research"),
            WikiTopic("Game theory", "Game_theory"),
            WikiTopic("Financial mathematics", "Mathematical_finance"),
        )),
        WikiSubgroup("Discrete mathematics", "Discrete_mathematics", listOf(
            WikiTopic("Combinatorics", "Combinatorics"),
            WikiTopic("Graph theory", "Graph_theory"),
            WikiTopic("Cryptography", "Cryptography"),
            WikiTopic("Information theory", "Information_theory"),
            WikiTopic("Computability theory", "Computability_theory"),
        )),
    )),

    // ── ASTRONOMY ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Astronomy", "Astronomy", listOf(
        WikiSubgroup("Solar System", "Solar_System", listOf(
            WikiTopic("Planets", "Planets_of_the_Solar_System"),
            WikiTopic("Moons", "Moons_of_the_Solar_System"),
            WikiTopic("Asteroids", "Asteroids"),
            WikiTopic("Comets", "Comets"),
            WikiTopic("Dwarf planets", "Dwarf_planets"),
            WikiTopic("The Sun", "Sun"),
        )),
        WikiSubgroup("Stars & stellar objects", "Stars", listOf(
            WikiTopic("Star types", "Star"),
            WikiTopic("Black holes", "Black_holes"),
            WikiTopic("Neutron stars", "Neutron_stars"),
            WikiTopic("Supernovae", "Supernovae"),
            WikiTopic("Nebulae", "Nebulae"),
            WikiTopic("Binary stars", "Binary_stars"),
        )),
        WikiSubgroup("Galaxies & cosmology", "Galaxies", listOf(
            WikiTopic("Galaxies", "Galaxies"),
            WikiTopic("Galaxy clusters", "Galaxy_clusters"),
            WikiTopic("Cosmology", "Cosmology"),
            WikiTopic("Dark matter", "Dark_matter"),
            WikiTopic("Dark energy", "Dark_energy"),
            WikiTopic("Big Bang", "Big_Bang"),
        )),
        WikiSubgroup("Exploration", "Space_exploration", listOf(
            WikiTopic("Space missions", "Space_missions"),
            WikiTopic("Space telescopes", "Space_telescopes"),
            WikiTopic("Exoplanets", "Exoplanets"),
            WikiTopic("Astronauts", "Astronauts"),
            WikiTopic("Space stations", "Space_stations"),
        )),
    )),

    // ── HISTORY ───────────────────────────────────────────────────────────────
    WikiTopicGroup("History", "History", listOf(
        WikiSubgroup("Ancient world", "Ancient_history", listOf(
            WikiTopic("Ancient Egypt", "Ancient_Egypt"),
            WikiTopic("Ancient Greece", "Ancient_Greece"),
            WikiTopic("Ancient Rome", "Ancient_Rome"),
            WikiTopic("Ancient China", "History_of_China"),
            WikiTopic("Ancient India", "History_of_India"),
            WikiTopic("Mesopotamia", "Mesopotamia"),
            WikiTopic("Ancient Near East", "Ancient_Near_East"),
            WikiTopic("Prehistoric cultures", "Prehistoric_cultures"),
        )),
        WikiSubgroup("Medieval world", "Middle_Ages", listOf(
            WikiTopic("Byzantine Empire", "Byzantine_Empire"),
            WikiTopic("Islamic Golden Age", "Islamic_Golden_Age"),
            WikiTopic("Crusades", "Crusades"),
            WikiTopic("Mongol Empire", "Mongol_Empire"),
            WikiTopic("Viking Age", "Viking_Age"),
            WikiTopic("Feudalism", "Feudalism"),
            WikiTopic("Medieval Europe", "Medieval_Europe"),
        )),
        WikiSubgroup("Early modern", "Early_modern_period", listOf(
            WikiTopic("Renaissance", "Renaissance"),
            WikiTopic("Age of Exploration", "Age_of_Discovery"),
            WikiTopic("Reformation", "Reformation"),
            WikiTopic("Ottoman Empire", "Ottoman_Empire"),
            WikiTopic("Mughal Empire", "Mughal_Empire"),
            WikiTopic("Scientific Revolution", "Scientific_Revolution"),
        )),
        WikiSubgroup("Modern era", "Modern_history", listOf(
            WikiTopic("Industrial Revolution", "Industrial_Revolution"),
            WikiTopic("French Revolution", "French_Revolution"),
            WikiTopic("Colonialism", "Colonialism"),
            WikiTopic("World War I", "World_War_I"),
            WikiTopic("World War II", "World_War_II"),
            WikiTopic("Cold War", "Cold_War"),
            WikiTopic("Decolonisation", "Decolonisation"),
        )),
        WikiSubgroup("History by region", "History_by_region", listOf(
            WikiTopic("South America", "History_of_South_America"),
            WikiTopic("Latin America", "History_of_Latin_America"),
            WikiTopic("North America", "History_of_North_America"),
            WikiTopic("Africa", "History_of_Africa"),
            WikiTopic("Asia", "History_of_Asia"),
            WikiTopic("Europe", "History_of_Europe"),
            WikiTopic("Oceania", "History_of_Oceania"),
            WikiTopic("Middle East", "History_of_the_Middle_East"),
        )),
    )),

    // ── GEOGRAPHY ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Geography", "Geography", listOf(
        WikiSubgroup("Physical geography", "Physical_geography", listOf(
            WikiTopic("Mountains", "Mountains"),
            WikiTopic("Rivers", "Rivers"),
            WikiTopic("Oceans", "Oceans"),
            WikiTopic("Islands", "Islands"),
            WikiTopic("Deserts", "Deserts"),
            WikiTopic("Forests", "Forests"),
            WikiTopic("Volcanoes", "Volcanoes"),
            WikiTopic("Lakes", "Lakes"),
            WikiTopic("Glaciers", "Glaciers"),
            WikiTopic("Caves", "Caves"),
        )),
        WikiSubgroup("Regions & countries", "Countries", listOf(
            WikiTopic("Countries", "Countries"),
            WikiTopic("Cities", "Cities"),
            WikiTopic("Africa", "Africa"),
            WikiTopic("Asia", "Asia"),
            WikiTopic("Europe", "Europe"),
            WikiTopic("Americas", "Americas"),
            WikiTopic("Oceania", "Oceania"),
        )),
        WikiSubgroup("Climate & oceans", "Climatology", listOf(
            WikiTopic("Climate zones", "Climate"),
            WikiTopic("Oceanography", "Oceanography"),
            WikiTopic("Atmospheric science", "Atmospheric_science"),
            WikiTopic("Extreme weather", "Extreme_weather"),
            WikiTopic("Plate tectonics", "Plate_tectonics"),
        )),
    )),

    // ── COMPUTING ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Computing", "Computing", listOf(
        WikiSubgroup("Theoretical CS", "Theoretical_computer_science", listOf(
            WikiTopic("Algorithms", "Algorithms"),
            WikiTopic("Data structures", "Data_structures"),
            WikiTopic("Complexity theory", "Computational_complexity_theory"),
            WikiTopic("Automata theory", "Automata_theory"),
            WikiTopic("Information theory", "Information_theory"),
        )),
        WikiSubgroup("Software", "Software", listOf(
            WikiTopic("Programming languages", "Programming_languages"),
            WikiTopic("Operating systems", "Operating_systems"),
            WikiTopic("Databases", "Databases"),
            WikiTopic("Software engineering", "Software_engineering"),
            WikiTopic("Compilers", "Compilers"),
            WikiTopic("Version control", "Version_control"),
        )),
        WikiSubgroup("AI & machine learning", "Artificial_intelligence", listOf(
            WikiTopic("Machine learning", "Machine_learning"),
            WikiTopic("Deep learning", "Deep_learning"),
            WikiTopic("Computer vision", "Computer_vision"),
            WikiTopic("Natural language processing", "Natural_language_processing"),
            WikiTopic("Robotics", "Robotics"),
            WikiTopic("Reinforcement learning", "Reinforcement_learning"),
        )),
        WikiSubgroup("Networks & security", "Computer_networks", listOf(
            WikiTopic("Computer networks", "Computer_networks"),
            WikiTopic("Internet", "Internet"),
            WikiTopic("Cybersecurity", "Computer_security"),
            WikiTopic("Cryptography", "Cryptography"),
            WikiTopic("Distributed systems", "Distributed_computing"),
        )),
        WikiSubgroup("Graphics & games", "Computer_graphics", listOf(
            WikiTopic("Computer graphics", "Computer_graphics"),
            WikiTopic("Video games", "Video_games"),
            WikiTopic("Game engines", "Game_engine"),
            WikiTopic("Virtual reality", "Virtual_reality"),
        )),
    )),

    // ── MEDICINE ──────────────────────────────────────────────────────────────
    WikiTopicGroup("Medicine", "Medicine", listOf(
        WikiSubgroup("Anatomy & physiology", "Anatomy", listOf(
            WikiTopic("Human anatomy", "Human_anatomy"),
            WikiTopic("Physiology", "Physiology"),
            WikiTopic("Neuroscience", "Neuroscience"),
            WikiTopic("Cardiovascular system", "Cardiovascular_system"),
            WikiTopic("Immune system", "Immune_system"),
            WikiTopic("Endocrine system", "Endocrine_system"),
        )),
        WikiSubgroup("Clinical specialties", "Medical_specialties", listOf(
            WikiTopic("Neurology", "Neurology"),
            WikiTopic("Cardiology", "Cardiology"),
            WikiTopic("Oncology", "Oncology"),
            WikiTopic("Psychiatry", "Psychiatry"),
            WikiTopic("Surgery", "Surgery"),
            WikiTopic("Pediatrics", "Pediatrics"),
            WikiTopic("Dermatology", "Dermatology"),
            WikiTopic("Orthopedics", "Orthopedic_surgery"),
        )),
        WikiSubgroup("Disease & treatment", "Infectious_diseases", listOf(
            WikiTopic("Infectious diseases", "Infectious_diseases"),
            WikiTopic("Genetic diseases", "Genetic_disorders"),
            WikiTopic("Pharmacology", "Pharmacology"),
            WikiTopic("Vaccines", "Vaccine"),
            WikiTopic("Antibiotics", "Antibiotics"),
            WikiTopic("Cancer", "Cancer"),
        )),
        WikiSubgroup("Health & nutrition", "Public_health", listOf(
            WikiTopic("Nutrition", "Nutrition"),
            WikiTopic("Public health", "Public_health"),
            WikiTopic("Epidemiology", "Epidemiology"),
            WikiTopic("Mental health", "Mental_health"),
            WikiTopic("Sports medicine", "Sports_medicine"),
        )),
    )),

    // ── ENGINEERING ───────────────────────────────────────────────────────────
    WikiTopicGroup("Engineering", "Engineering", listOf(
        WikiSubgroup("Mechanical & civil", "Mechanical_engineering", listOf(
            WikiTopic("Mechanical engineering", "Mechanical_engineering"),
            WikiTopic("Civil engineering", "Civil_engineering"),
            WikiTopic("Structural engineering", "Structural_engineering"),
            WikiTopic("Thermodynamics (eng)", "Engineering_thermodynamics"),
            WikiTopic("Fluid mechanics", "Fluid_mechanics"),
        )),
        WikiSubgroup("Electrical & electronic", "Electrical_engineering", listOf(
            WikiTopic("Electrical engineering", "Electrical_engineering"),
            WikiTopic("Electronics", "Electronics"),
            WikiTopic("Signal processing", "Signal_processing"),
            WikiTopic("Control systems", "Control_theory"),
            WikiTopic("Power engineering", "Power_engineering"),
        )),
        WikiSubgroup("Chemical & materials", "Chemical_engineering", listOf(
            WikiTopic("Chemical engineering", "Chemical_engineering"),
            WikiTopic("Materials science", "Materials_science"),
            WikiTopic("Metallurgy", "Metallurgy"),
            WikiTopic("Polymer engineering", "Polymer_engineering"),
            WikiTopic("Nanotechnology", "Nanotechnology"),
        )),
        WikiSubgroup("Aerospace & other", "Aerospace_engineering", listOf(
            WikiTopic("Aerospace engineering", "Aerospace_engineering"),
            WikiTopic("Biomedical engineering", "Biomedical_engineering"),
            WikiTopic("Environmental engineering", "Environmental_engineering"),
            WikiTopic("Nuclear engineering", "Nuclear_engineering"),
        )),
    )),

    // ── ARTS ──────────────────────────────────────────────────────────────────
    WikiTopicGroup("Art", "Art", listOf(
        WikiSubgroup("Visual art movements", "Art_movements", listOf(
            WikiTopic("Renaissance art", "Renaissance_art"),
            WikiTopic("Baroque", "Baroque"),
            WikiTopic("Impressionism", "Impressionism"),
            WikiTopic("Modernism", "Modern_art"),
            WikiTopic("Surrealism", "Surrealism"),
            WikiTopic("Abstract art", "Abstract_art"),
            WikiTopic("Contemporary art", "Contemporary_art"),
        )),
        WikiSubgroup("Media & techniques", "Visual_arts", listOf(
            WikiTopic("Painting", "Painting"),
            WikiTopic("Sculpture", "Sculpture"),
            WikiTopic("Drawing", "Drawing"),
            WikiTopic("Photography", "Photography"),
            WikiTopic("Printmaking", "Printmaking"),
            WikiTopic("Digital art", "Digital_art"),
        )),
        WikiSubgroup("Decorative & applied", "Decorative_arts", listOf(
            WikiTopic("Ceramics", "Ceramics"),
            WikiTopic("Textiles", "Textiles"),
            WikiTopic("Jewellery", "Jewellery"),
            WikiTopic("Glasswork", "Glass_art"),
            WikiTopic("Folk art", "Folk_art"),
        )),
    )),

    // ── ARCHITECTURE ──────────────────────────────────────────────────────────
    WikiTopicGroup("Architecture", "Architecture", listOf(
        WikiSubgroup("Historical styles", "Architectural_styles", listOf(
            WikiTopic("Ancient architecture", "Ancient_architecture"),
            WikiTopic("Classical architecture", "Classical_architecture"),
            WikiTopic("Gothic architecture", "Gothic_architecture"),
            WikiTopic("Islamic architecture", "Islamic_architecture"),
            WikiTopic("Renaissance architecture", "Renaissance_architecture"),
            WikiTopic("Baroque architecture", "Baroque_architecture"),
        )),
        WikiSubgroup("Modern styles", "Modern_architecture", listOf(
            WikiTopic("Modern architecture", "Modern_architecture"),
            WikiTopic("Art Deco", "Art_Deco"),
            WikiTopic("Brutalism", "Brutalism"),
            WikiTopic("Postmodern architecture", "Postmodern_architecture"),
            WikiTopic("Sustainable architecture", "Sustainable_architecture"),
        )),
        WikiSubgroup("Structure types", "Bridges", listOf(
            WikiTopic("Bridges", "Bridges"),
            WikiTopic("Skyscrapers", "Skyscrapers"),
            WikiTopic("Castles", "Castles"),
            WikiTopic("Cathedrals", "Cathedrals"),
            WikiTopic("Palaces", "Palaces"),
            WikiTopic("Temples", "Temples"),
        )),
    )),

    // ── MUSIC ─────────────────────────────────────────────────────────────────
    WikiTopicGroup("Music", "Music", listOf(
        WikiSubgroup("Classical & traditional", "Classical_music", listOf(
            WikiTopic("Classical music", "Classical_music"),
            WikiTopic("Opera", "Opera"),
            WikiTopic("Baroque music", "Baroque_music"),
            WikiTopic("Romantic music", "Romantic_music"),
            WikiTopic("Folk music", "Folk_music"),
            WikiTopic("World music", "World_music"),
        )),
        WikiSubgroup("Popular music", "Popular_music", listOf(
            WikiTopic("Jazz", "Jazz"),
            WikiTopic("Blues", "Blues"),
            WikiTopic("Rock music", "Rock_music"),
            WikiTopic("Punk rock", "Punk_rock"),
            WikiTopic("Heavy metal", "Heavy_metal_music"),
            WikiTopic("Electronic music", "Electronic_music"),
            WikiTopic("Hip hop", "Hip_hop_music"),
            WikiTopic("Reggae", "Reggae"),
            WikiTopic("Soul & R&B", "Rhythm_and_blues"),
        )),
        WikiSubgroup("Theory & production", "Music_theory", listOf(
            WikiTopic("Music theory", "Music_theory"),
            WikiTopic("Musical instruments", "Musical_instruments"),
            WikiTopic("Music production", "Record_production"),
            WikiTopic("Sound recording", "Sound_recording_and_reproduction"),
        )),
    )),

    // ── LITERATURE ────────────────────────────────────────────────────────────
    WikiTopicGroup("Literature", "Literature", listOf(
        WikiSubgroup("Genres", "Genre_fiction", listOf(
            WikiTopic("Science fiction", "Science_fiction"),
            WikiTopic("Fantasy", "Fantasy"),
            WikiTopic("Mystery & crime", "Mystery_fiction"),
            WikiTopic("Horror fiction", "Horror_fiction"),
            WikiTopic("Romance novels", "Romance_novel"),
            WikiTopic("Historical fiction", "Historical_fiction"),
            WikiTopic("Children's literature", "Children's_literature"),
        )),
        WikiSubgroup("Literary forms", "Literary_forms", listOf(
            WikiTopic("Poetry", "Poetry"),
            WikiTopic("Novels", "Novel"),
            WikiTopic("Short stories", "Short_story"),
            WikiTopic("Drama", "Drama"),
            WikiTopic("Essays", "Essay"),
            WikiTopic("Graphic novels", "Graphic_novel"),
        )),
        WikiSubgroup("By period", "Ancient_literature", listOf(
            WikiTopic("Ancient literature", "Ancient_literature"),
            WikiTopic("Medieval literature", "Medieval_literature"),
            WikiTopic("Renaissance literature", "Renaissance_literature"),
            WikiTopic("19th-century literature", "19th-century_literature"),
            WikiTopic("Modernist literature", "Modernist_literature"),
        )),
    )),

    // ── FILM ──────────────────────────────────────────────────────────────────
    WikiTopicGroup("Film", "Film", listOf(
        WikiSubgroup("Genres", "Film_genres", listOf(
            WikiTopic("Action films", "Action_film"),
            WikiTopic("Drama films", "Drama_film"),
            WikiTopic("Comedy films", "Comedy_film"),
            WikiTopic("Horror films", "Horror_film"),
            WikiTopic("Science fiction films", "Science_fiction_film"),
            WikiTopic("Documentary films", "Documentary_film"),
            WikiTopic("Animated films", "Animated_film"),
            WikiTopic("Film noir", "Film_noir"),
            WikiTopic("Thriller films", "Thriller_film"),
        )),
        WikiSubgroup("History & movements", "History_of_film", listOf(
            WikiTopic("Silent film", "Silent_film"),
            WikiTopic("Classic Hollywood", "Classical_Hollywood_cinema"),
            WikiTopic("New Wave cinema", "French_New_Wave"),
            WikiTopic("Independent film", "Independent_film"),
            WikiTopic("World cinema", "World_cinema"),
        )),
    )),

    // ── PHILOSOPHY ────────────────────────────────────────────────────────────
    WikiTopicGroup("Philosophy", "Philosophy", listOf(
        WikiSubgroup("Core branches", "Branches_of_philosophy", listOf(
            WikiTopic("Ethics", "Ethics"),
            WikiTopic("Metaphysics", "Metaphysics"),
            WikiTopic("Epistemology", "Epistemology"),
            WikiTopic("Logic", "Logic"),
            WikiTopic("Aesthetics", "Aesthetics"),
            WikiTopic("Philosophy of mind", "Philosophy_of_mind"),
        )),
        WikiSubgroup("Applied philosophy", "Applied_philosophy", listOf(
            WikiTopic("Political philosophy", "Political_philosophy"),
            WikiTopic("Philosophy of science", "Philosophy_of_science"),
            WikiTopic("Philosophy of language", "Philosophy_of_language"),
            WikiTopic("Philosophy of mathematics", "Philosophy_of_mathematics"),
            WikiTopic("Bioethics", "Bioethics"),
        )),
        WikiSubgroup("Schools of thought", "Philosophical_schools", listOf(
            WikiTopic("Existentialism", "Existentialism"),
            WikiTopic("Stoicism", "Stoicism"),
            WikiTopic("Empiricism", "Empiricism"),
            WikiTopic("Rationalism", "Rationalism"),
            WikiTopic("Pragmatism", "Pragmatism"),
            WikiTopic("Analytic philosophy", "Analytic_philosophy"),
            WikiTopic("Continental philosophy", "Continental_philosophy"),
        )),
    )),

    // ── RELIGION ──────────────────────────────────────────────────────────────
    WikiTopicGroup("Religion", "Religion", listOf(
        WikiSubgroup("Abrahamic religions", "Abrahamic_religions", listOf(
            WikiTopic("Christianity", "Christianity"),
            WikiTopic("Catholicism", "Catholic_Church"),
            WikiTopic("Protestantism", "Protestantism"),
            WikiTopic("Islam", "Islam"),
            WikiTopic("Sunni Islam", "Sunni_Islam"),
            WikiTopic("Shia Islam", "Shia_Islam"),
            WikiTopic("Judaism", "Judaism"),
        )),
        WikiSubgroup("Eastern religions", "Eastern_religions", listOf(
            WikiTopic("Buddhism", "Buddhism"),
            WikiTopic("Hinduism", "Hinduism"),
            WikiTopic("Sikhism", "Sikhism"),
            WikiTopic("Taoism", "Taoism"),
            WikiTopic("Confucianism", "Confucianism"),
            WikiTopic("Shinto", "Shinto"),
            WikiTopic("Jainism", "Jainism"),
        )),
        WikiSubgroup("Ancient & other", "Ancient_religions", listOf(
            WikiTopic("Ancient Egyptian religion", "Ancient_Egyptian_religion"),
            WikiTopic("Ancient Greek religion", "Ancient_Greek_religion"),
            WikiTopic("Norse religion", "Norse_paganism"),
            WikiTopic("Indigenous religions", "Ethnic_religion"),
            WikiTopic("New religious movements", "New_religious_movement"),
        )),
    )),

    // ── MYTHOLOGY ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Mythology", "Mythology", listOf(
        WikiSubgroup("European mythology", "European_mythology", listOf(
            WikiTopic("Greek mythology", "Greek_mythology"),
            WikiTopic("Roman mythology", "Roman_mythology"),
            WikiTopic("Norse mythology", "Norse_mythology"),
            WikiTopic("Celtic mythology", "Celtic_mythology"),
            WikiTopic("Slavic mythology", "Slavic_mythology"),
        )),
        WikiSubgroup("World mythology", "World_mythology", listOf(
            WikiTopic("Egyptian mythology", "Egyptian_mythology"),
            WikiTopic("Mesopotamian mythology", "Mesopotamian_mythology"),
            WikiTopic("Hindu mythology", "Hindu_mythology"),
            WikiTopic("Japanese mythology", "Japanese_mythology"),
            WikiTopic("Chinese mythology", "Chinese_mythology"),
            WikiTopic("Aztec mythology", "Aztec_mythology"),
            WikiTopic("Mayan mythology", "Maya_mythology"),
        )),
    )),

    // ── PSYCHOLOGY ────────────────────────────────────────────────────────────
    WikiTopicGroup("Psychology", "Psychology", listOf(
        WikiSubgroup("Core fields", "Branches_of_psychology", listOf(
            WikiTopic("Cognitive psychology", "Cognitive_psychology"),
            WikiTopic("Social psychology", "Social_psychology"),
            WikiTopic("Developmental psychology", "Developmental_psychology"),
            WikiTopic("Behavioral psychology", "Behaviorism"),
            WikiTopic("Neuropsychology", "Neuropsychology"),
            WikiTopic("Personality psychology", "Personality_psychology"),
        )),
        WikiSubgroup("Clinical & applied", "Clinical_psychology", listOf(
            WikiTopic("Clinical psychology", "Clinical_psychology"),
            WikiTopic("Psychotherapy", "Psychotherapy"),
            WikiTopic("Abnormal psychology", "Abnormal_psychology"),
            WikiTopic("Forensic psychology", "Forensic_psychology"),
            WikiTopic("Health psychology", "Health_psychology"),
            WikiTopic("Positive psychology", "Positive_psychology"),
        )),
    )),

    // ── ECONOMICS ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Economics", "Economics", listOf(
        WikiSubgroup("Economic theory", "Economic_theory", listOf(
            WikiTopic("Microeconomics", "Microeconomics"),
            WikiTopic("Macroeconomics", "Macroeconomics"),
            WikiTopic("Behavioural economics", "Behavioral_economics"),
            WikiTopic("Development economics", "Development_economics"),
            WikiTopic("Labour economics", "Labour_economics"),
            WikiTopic("Welfare economics", "Welfare_economics"),
        )),
        WikiSubgroup("Finance & markets", "Finance", listOf(
            WikiTopic("Finance", "Finance"),
            WikiTopic("Banking", "Banking"),
            WikiTopic("Financial markets", "Financial_market"),
            WikiTopic("International trade", "International_trade"),
            WikiTopic("Public economics", "Public_economics"),
        )),
    )),

    // ── POLITICS ──────────────────────────────────────────────────────────────
    WikiTopicGroup("Politics", "Politics", listOf(
        WikiSubgroup("Political theory", "Political_philosophy", listOf(
            WikiTopic("Democracy", "Democracy"),
            WikiTopic("Liberalism", "Liberalism"),
            WikiTopic("Conservatism", "Conservatism"),
            WikiTopic("Socialism", "Socialism"),
            WikiTopic("Anarchism", "Anarchism"),
            WikiTopic("Nationalism", "Nationalism"),
        )),
        WikiSubgroup("International & governance", "International_relations", listOf(
            WikiTopic("International relations", "International_relations"),
            WikiTopic("Diplomacy", "Diplomacy"),
            WikiTopic("Elections", "Elections"),
            WikiTopic("Government systems", "Government"),
            WikiTopic("Political parties", "Political_parties"),
            WikiTopic("Human rights", "Human_rights"),
        )),
    )),

    // ── LAW ───────────────────────────────────────────────────────────────────
    WikiTopicGroup("Law", "Law", listOf(
        WikiSubgroup("Legal systems", "Legal_systems", listOf(
            WikiTopic("Common law", "Common_law"),
            WikiTopic("Civil law systems", "Civil_law_(legal_system)"),
            WikiTopic("Constitutional law", "Constitutional_law"),
            WikiTopic("International law", "International_law"),
        )),
        WikiSubgroup("Legal fields", "Branches_of_law", listOf(
            WikiTopic("Criminal law", "Criminal_law"),
            WikiTopic("Contract law", "Contract"),
            WikiTopic("Intellectual property", "Intellectual_property"),
            WikiTopic("Human rights law", "International_human_rights_law"),
            WikiTopic("Environmental law", "Environmental_law"),
        )),
    )),

    // ── SPORTS ────────────────────────────────────────────────────────────────
    WikiTopicGroup("Sports", "Sports", listOf(
        WikiSubgroup("Team sports", "Team_sports", listOf(
            WikiTopic("Football (soccer)", "Association_football"),
            WikiTopic("Basketball", "Basketball"),
            WikiTopic("Rugby", "Rugby_football"),
            WikiTopic("Cricket", "Cricket"),
            WikiTopic("Baseball", "Baseball"),
            WikiTopic("Ice hockey", "Ice_hockey"),
            WikiTopic("Volleyball", "Volleyball"),
        )),
        WikiSubgroup("Individual sports", "Individual_sports", listOf(
            WikiTopic("Tennis", "Tennis"),
            WikiTopic("Athletics", "Athletics_(sport)"),
            WikiTopic("Swimming", "Swimming_(sport)"),
            WikiTopic("Cycling", "Cycling"),
            WikiTopic("Gymnastics", "Gymnastics"),
            WikiTopic("Boxing", "Boxing"),
            WikiTopic("Martial arts", "Martial_arts"),
        )),
        WikiSubgroup("Other sports", "Sports_by_type", listOf(
            WikiTopic("Winter sports", "Winter_sports"),
            WikiTopic("Water sports", "Water_sports"),
            WikiTopic("Motorsport", "Motorsport"),
            WikiTopic("Equestrian sports", "Equestrian_sports"),
            WikiTopic("Olympic Games", "Olympic_Games"),
        )),
        WikiSubgroup("Motorsport", "Motorsport", listOf(
            WikiTopic("Formula One", "Formula_One"),
            WikiTopic("Formula One drivers", "Formula_One_drivers"),
            WikiTopic("Formula One teams", "Formula_One_teams"),
            WikiTopic("Formula One circuits", "Formula_One_circuits"),
            WikiTopic("World Rally Championship", "World_Rally_Championship"),
            WikiTopic("Motorcycle racing", "Motorcycle_racing"),
            WikiTopic("NASCAR", "NASCAR"),
        )),
    )),

    // ── ENVIRONMENT ───────────────────────────────────────────────────────────
    WikiTopicGroup("Environment", "Environment", listOf(
        WikiSubgroup("Climate & atmosphere", "Climate_change", listOf(
            WikiTopic("Climate change", "Climate_change"),
            WikiTopic("Global warming", "Global_warming"),
            WikiTopic("Atmospheric science", "Atmospheric_science"),
            WikiTopic("Weather & storms", "Meteorology"),
        )),
        WikiSubgroup("Ecology & biodiversity", "Biodiversity", listOf(
            WikiTopic("Biodiversity", "Biodiversity"),
            WikiTopic("Endangered species", "Endangered_species"),
            WikiTopic("Conservation", "Conservation_biology"),
            WikiTopic("Deforestation", "Deforestation"),
            WikiTopic("Ocean conservation", "Marine_conservation"),
        )),
        WikiSubgroup("Pollution & energy", "Pollution", listOf(
            WikiTopic("Pollution", "Pollution"),
            WikiTopic("Renewable energy", "Renewable_energy"),
            WikiTopic("Fossil fuels", "Fossil_fuel"),
            WikiTopic("Nuclear power", "Nuclear_power"),
            WikiTopic("Waste management", "Waste_management"),
        )),
    )),

    // ── FOOD ──────────────────────────────────────────────────────────────────
    WikiTopicGroup("Food", "Food", listOf(
        WikiSubgroup("Cooking & methods", "Cooking", listOf(
            WikiTopic("Cooking techniques", "Cooking"),
            WikiTopic("Baking", "Baking"),
            WikiTopic("Fermented foods", "Fermented_foods"),
            WikiTopic("Food preservation", "Food_preservation"),
        )),
        WikiSubgroup("World cuisines", "Cuisine", listOf(
            WikiTopic("Asian cuisine", "Asian_cuisine"),
            WikiTopic("European cuisine", "European_cuisine"),
            WikiTopic("Middle Eastern cuisine", "Middle_Eastern_cuisine"),
            WikiTopic("African cuisine", "African_cuisine"),
            WikiTopic("Latin American cuisine", "Latin_American_cuisine"),
        )),
        WikiSubgroup("Ingredients", "Ingredients", listOf(
            WikiTopic("Vegetables", "Vegetable"),
            WikiTopic("Fruits", "Fruit"),
            WikiTopic("Herbs & spices", "Herbs_and_spices"),
            WikiTopic("Cheese", "Cheese"),
            WikiTopic("Beverages", "Beverage"),
            WikiTopic("Chocolate", "Chocolate"),
        )),
    )),

    // ── LANGUAGE ──────────────────────────────────────────────────────────────
    WikiTopicGroup("Language", "Language", listOf(
        WikiSubgroup("Linguistics", "Linguistics", listOf(
            WikiTopic("Grammar", "Grammar"),
            WikiTopic("Etymology", "Etymology"),
            WikiTopic("Phonetics", "Phonetics"),
            WikiTopic("Semantics", "Semantics"),
            WikiTopic("Pragmatics", "Pragmatics"),
            WikiTopic("Writing systems", "Writing_systems"),
        )),
        WikiSubgroup("Language families", "Language_families", listOf(
            WikiTopic("Indo-European languages", "Indo-European_languages"),
            WikiTopic("Semitic languages", "Semitic_languages"),
            WikiTopic("Sino-Tibetan languages", "Sino-Tibetan_languages"),
            WikiTopic("Niger-Congo languages", "Niger-Congo_languages"),
            WikiTopic("Austronesian languages", "Austronesian_languages"),
            WikiTopic("Constructed languages", "Constructed_languages"),
            WikiTopic("Sign languages", "Sign_languages"),
        )),
    )),

    // ── EARTH SCIENCES ────────────────────────────────────────────────────────
    WikiTopicGroup("Earth Sciences", "Earth_sciences", listOf(
        WikiSubgroup("Geology", "Geology", listOf(
            WikiTopic("Rocks & minerals", "Minerals"),
            WikiTopic("Geomorphology", "Geomorphology"),
            WikiTopic("Volcanology", "Volcanology"),
            WikiTopic("Seismology", "Seismology"),
            WikiTopic("Structural geology", "Structural_geology"),
            WikiTopic("Petrology", "Petrology"),
        )),
        WikiSubgroup("Paleontology", "Paleontology", listOf(
            WikiTopic("Dinosaurs (paleo)", "Dinosaurs"),
            WikiTopic("Prehistoric mammals", "Prehistoric_mammals"),
            WikiTopic("Marine reptiles", "Marine_reptile"),
            WikiTopic("Fossil plants", "Paleobotany"),
            WikiTopic("Human evolution", "Human_evolution"),
            WikiTopic("Mass extinctions", "Extinction_event"),
        )),
        WikiSubgroup("Atmosphere & oceans", "Physical_oceanography", listOf(
            WikiTopic("Oceanography", "Oceanography"),
            WikiTopic("Meteorology", "Meteorology"),
            WikiTopic("Climatology", "Climatology"),
            WikiTopic("Hydrology", "Hydrology"),
            WikiTopic("Glaciology", "Glaciology"),
        )),
    )),

    // ── TRANSPORT ─────────────────────────────────────────────────────────────
    WikiTopicGroup("Transport", "Transport", listOf(
        WikiSubgroup("Aviation", "Aviation", listOf(
            WikiTopic("Aircraft", "Aircraft"),
            WikiTopic("Airlines", "Airlines"),
            WikiTopic("Airports", "Airports"),
            WikiTopic("Aviation history", "History_of_aviation"),
            WikiTopic("Military aircraft", "Military_aircraft"),
            WikiTopic("Helicopters", "Helicopters"),
        )),
        WikiSubgroup("Maritime", "Maritime_transport", listOf(
            WikiTopic("Ships", "Ships"),
            WikiTopic("Sailing vessels", "Sailing_ship"),
            WikiTopic("Naval history", "Naval_history"),
            WikiTopic("Lighthouses", "Lighthouses"),
            WikiTopic("Submarines", "Submarines"),
        )),
        WikiSubgroup("Rail", "Rail_transport", listOf(
            WikiTopic("Railways", "Rail_transport"),
            WikiTopic("Locomotives", "Locomotives"),
            WikiTopic("Metro & rapid transit", "Rapid_transit"),
            WikiTopic("Historic railways", "History_of_rail_transport"),
        )),
        WikiSubgroup("Road & automotive", "Road_transport", listOf(
            WikiTopic("Automobiles", "Automobile"),
            WikiTopic("Motorcycles", "Motorcycles"),
            WikiTopic("Roads & highways", "Road"),
            WikiTopic("History of motoring", "History_of_the_automobile"),
        )),
    )),

    // ── SOCIAL SCIENCES ───────────────────────────────────────────────────────
    WikiTopicGroup("Social Sciences", "Social_sciences", listOf(
        WikiSubgroup("Sociology", "Sociology", listOf(
            WikiTopic("Social theory", "Social_theory"),
            WikiTopic("Social movements", "Social_movements"),
            WikiTopic("Cultural sociology", "Cultural_sociology"),
            WikiTopic("Urban sociology", "Urban_sociology"),
            WikiTopic("Gender studies", "Gender_studies"),
        )),
        WikiSubgroup("Anthropology", "Anthropology", listOf(
            WikiTopic("Cultural anthropology", "Cultural_anthropology"),
            WikiTopic("Physical anthropology", "Biological_anthropology"),
            WikiTopic("Linguistics (anthro)", "Linguistic_anthropology"),
            WikiTopic("Social anthropology", "Social_anthropology"),
        )),
        WikiSubgroup("Archaeology", "Archaeology", listOf(
            WikiTopic("Prehistoric archaeology", "Prehistoric_archaeology"),
            WikiTopic("Classical archaeology", "Classical_archaeology"),
            WikiTopic("Archaeological cultures", "Archaeological_culture"),
            WikiTopic("Archaeological sites", "Archaeological_sites"),
            WikiTopic("Archaeometallurgy", "Archaeometallurgy"),
        )),
    )),

    // ── GAMES ─────────────────────────────────────────────────────────────────
    WikiTopicGroup("Games", "Games", listOf(
        WikiSubgroup("Board & tabletop", "Board_games", listOf(
            WikiTopic("Board games", "Board_games"),
            WikiTopic("Card games", "Card_games"),
            WikiTopic("Chess", "Chess"),
            WikiTopic("Go (game)", "Go_(game)"),
            WikiTopic("Tabletop RPGs", "Tabletop_role-playing_games"),
            WikiTopic("Wargames", "Wargame"),
        )),
        WikiSubgroup("Video game genres", "Video_game_genres", listOf(
            WikiTopic("Action-adventure games", "Action-adventure_game"),
            WikiTopic("Role-playing games", "Role-playing_video_game"),
            WikiTopic("Strategy games", "Strategy_video_game"),
            WikiTopic("Simulation games", "Simulation_video_game"),
            WikiTopic("Puzzle games", "Puzzle_video_game"),
            WikiTopic("Fighting games", "Fighting_game"),
            WikiTopic("Sports games", "Sports_game"),
        )),
        WikiSubgroup("Sport & outdoor games", "Sport", listOf(
            WikiTopic("Traditional games", "Traditional_games"),
            WikiTopic("Casino games", "Casino_games"),
            WikiTopic("Gambling", "Gambling"),
        )),
    )),

    // ── FASHION & DESIGN ──────────────────────────────────────────────────────
    WikiTopicGroup("Fashion & Design", "Fashion", listOf(
        WikiSubgroup("Fashion history", "History_of_fashion", listOf(
            WikiTopic("Fashion history", "History_of_fashion"),
            WikiTopic("Fashion houses", "Fashion_houses"),
            WikiTopic("Haute couture", "Haute_couture"),
            WikiTopic("Street fashion", "Street_fashion"),
            WikiTopic("Historical dress", "History_of_clothing"),
        )),
        WikiSubgroup("Design disciplines", "Design", listOf(
            WikiTopic("Industrial design", "Industrial_design"),
            WikiTopic("Graphic design", "Graphic_design"),
            WikiTopic("Typography", "Typography"),
            WikiTopic("Interior design", "Interior_design"),
            WikiTopic("Product design", "Product_design"),
            WikiTopic("UX & interaction", "User_experience_design"),
        )),
        WikiSubgroup("Textiles & materials", "Textile", listOf(
            WikiTopic("Textile arts", "Textile"),
            WikiTopic("Weaving", "Weaving"),
            WikiTopic("Knitting & crochet", "Knitting"),
            WikiTopic("Leather goods", "Leather"),
        )),
    )),

    // ── PERFORMING ARTS ───────────────────────────────────────────────────────
    WikiTopicGroup("Performing Arts", "Performing_arts", listOf(
        WikiSubgroup("Theatre", "Theatre", listOf(
            WikiTopic("Theatre history", "History_of_theatre"),
            WikiTopic("Greek drama", "Ancient_Greek_theatre"),
            WikiTopic("Musical theatre", "Musical_theatre"),
            WikiTopic("Puppetry", "Puppetry"),
            WikiTopic("Stage design", "Scenic_design"),
            WikiTopic("Contemporary theatre", "Contemporary_theatre"),
        )),
        WikiSubgroup("Dance", "Dance", listOf(
            WikiTopic("Ballet", "Ballet"),
            WikiTopic("Contemporary dance", "Contemporary_dance"),
            WikiTopic("Folk dance", "Folk_dance"),
            WikiTopic("Latin dance", "Latin_dance"),
            WikiTopic("Hip-hop dance", "Hip-hop_dance"),
            WikiTopic("Dance history", "History_of_dance"),
        )),
        WikiSubgroup("Circus & spectacle", "Circus", listOf(
            WikiTopic("Circus arts", "Circus"),
            WikiTopic("Acrobatics", "Acrobatics"),
            WikiTopic("Magic & illusion", "Magic_(illusion)"),
        )),
    )),

    // ── TELEVISION & RADIO ────────────────────────────────────────────────────
    WikiTopicGroup("Television & Radio", "Television", listOf(
        WikiSubgroup("TV genres", "Television_genres", listOf(
            WikiTopic("Drama series", "Television_drama"),
            WikiTopic("Sitcoms", "Sitcom"),
            WikiTopic("Reality TV", "Reality_television"),
            WikiTopic("Documentary TV", "Documentary_film"),
            WikiTopic("Science fiction TV", "Science_fiction_on_television"),
            WikiTopic("Animated series", "Animated_series"),
        )),
        WikiSubgroup("TV history & industry", "History_of_television", listOf(
            WikiTopic("History of television", "History_of_television"),
            WikiTopic("Broadcasting", "Broadcasting"),
            WikiTopic("Streaming services", "Streaming_service"),
        )),
        WikiSubgroup("Radio", "Radio", listOf(
            WikiTopic("Radio history", "History_of_radio"),
            WikiTopic("Radio technology", "Radio"),
            WikiTopic("Podcasting", "Podcast"),
        )),
    )),
)

/** Every selectable topic across all three levels */
val ALL_TOPICS: List<WikiTopic> = TOPIC_GROUPS.flatMap { group ->
    listOf(WikiTopic(group.label, group.category)) +
    group.subgroups.flatMap { sub ->
        listOf(WikiTopic(sub.label, sub.category)) + sub.children
    }
}

// ── Category search response (list=search with incategory: operator) ─────────
@Serializable
data class WikiApiCategorySearchResponse(
    val query: WikiCategorySearchQuery? = null
)

@Serializable
data class WikiCategorySearchQuery(
    val searchinfo: WikiSearchInfo? = null,
    val search: List<WikiCategorySearchResult> = emptyList()
)

/** Carries the total article count for a category search — used for random-offset pagination. */
@Serializable
data class WikiSearchInfo(
    val totalhits: Int = 0
)

@Serializable
data class WikiCategorySearchResult(
    val title: String,
    val pageid: Int = 0,
    /** HTML snippet returned by `srprop=snippet`; used to reject clearly unrelated search hits. */
    val snippet: String = ""
)

// ── Page-info response (action=query&prop=info|pageimages) ── stub + image detection ──
@Serializable
data class WikiApiPageInfoResponse(
    val query: WikiPageInfoQuery? = null
)

@Serializable
data class WikiPageInfoQuery(
    val pages: List<WikiPageInfo> = emptyList()
)

/**
 * Minimal page info returned by `prop=info|pageimages`. [length] is the wikitext size in
 * bytes; articles below ~5 000 bytes are typically stubs with very little actual content.
 * [thumbnail] is non-null only when the article has a lead image — used to batch-check the
 * "only show articles with images" filter without an extra round trip per candidate.
 */
@Serializable
data class WikiPageInfo(
    val pageid: Int = 0,
    val title: String = "",
    val length: Int = 0,
    val missing: Boolean = false,
    val thumbnail: WikiPhoto? = null,
    val categories: List<WikiCategoryMember> = emptyList(),
    /** Wikidata short description ("Genus of plants"); null when the article has none. */
    val description: String? = null,
    /** True for redirect pages: they point at another article and must never be picked. */
    val redirect: Boolean = false
)
