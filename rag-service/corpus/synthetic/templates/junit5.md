# Modèle de test JUnit 5 (synthétique)

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CvScreeningTest {

    @Test
    void r1_un_score_et_une_explication_sont_produits() {
        Resultat r = new ServiceCribleLocal().evaluer("Profil fictif Java Spring");
        assertTrue(r.score() >= 0 && r.score() <= 100);
        assertFalse(r.explication().isBlank());
    }

    @Test
    void r2_la_decision_finale_reste_humaine() {
        assertTrue(new ServiceCribleLocal().message().contains("recruteur"));
    }
}
```

Règles : pas d'attribut protégé dans le calcul ; le score est une aide à la décision, jamais une décision.
