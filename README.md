
# VZG Repository

## Installation Instructions

* run `mvn clean install`
* copy jar to ~/.mycore/(dev-)mir/lib/

## Development

You can add these to your ~/.mycore/(dev-)mir/.mycore.properties
```
MCR.Developer.Resource.Override=/path/to/reposis_vzg/src/main/resources
MCR.LayoutService.LastModifiedCheckPeriod=0
MCR.UseXSLTemplateCache=false
MCR.SASS.DeveloperMode=true
```

## Integrationstests

`VZGWorkflowIT` testet die Workflow-Anforderungen über Selenium gegen eine echte
Umgebung (Solr, Tomcat, MIR-Webapp): Nutzeranlage über die Oberfläche, PPN-Import,
Volltext-Upload, URN-Vergabe und Publizieren, je Rolle (Admin, Editor, Creator, Gast).

```
CI=true SELENIUM_HEADLESS=true mvn clean install
```

Hinweise:

* Benötigt Java 21 (Solr 9 startet nicht mit Java 25) und freie Ports 9107/9108/8292.
* Der PPN-Import lädt die Testdaten live über unapi.k10plus.de (PPN 198562268),
  braucht also Internetzugang.
* Die Tests laufen gegen die MIR-Version aus `mycore.version` im POM
  (Snapshot, wie die laufenden Instanzen).
* Ergebnisse und Screenshots: `target/failsafe-reports/`.

## Anforderungen Workflow

Die Workflow-Box über den Metadaten ist die zentrale Arbeitsoberfläche. Folgende
Anforderungen sind umgesetzt:

### Rollen und Sichtbarkeit

* Der Editor (Rolle `editor`) sieht in der Workflow-Box dieselben Möglichkeiten
  wie der Creator in MIR. Der Creator sieht sie nur für seine eigenen Dokumente,
  der Editor für alle Dokumente. Admins werden wie Editoren behandelt.
* Das Aktionsmenü auf der Detailseite wird nur für Admins (Rolle `admin`)
  angezeigt. Alle anderen Rollen arbeiten ausschließlich über die Workflow-Box.

### Aktionen in der Workflow-Box (Status "eingereicht")

* Das Bearbeiten des Dokuments ist nicht mehr möglich. Der Bearbeiten-Link wurde
  aus der Workflow-Box entfernt (Status "eingereicht" und "wird bearbeitet").
* Das Dokument kann gelöscht werden (nur im Status "eingereicht", nur mit
  Löschrecht auf dem Objekt). Der Löschen-Link steht immer als letzter Eintrag
  der Liste und wird in der Bootstrap-Gefahrenfarbe (`text-danger`) angezeigt.
* Eine URN kann vergeben werden. Bedingungen:
  * ein Dokument (Derivat mit Hauptdatei) wurde hochgeladen
  * es ist noch keine URN vergeben
  * der Nutzer hat die Rechte `writedb` und `register-DNBURN` auf dem Objekt
* Eine Lizenz kann über ein Dropdown in der Workflow-Box gesetzt werden (Klassifikation
  `mir_licenses`). Auswahl setzt sofort `mods:accessCondition[@type='use and reproduction']`
  über das neue `VZGLicenseServlet` (benötigt Schreibrecht auf dem Objekt, wie die
  übrigen Workflow-Aktionen).

### Publizieren

* Publizieren ist nur möglich, wenn ein Dokument hochgeladen wurde.
* Publizieren dürfen nur Bearbeiter (Rolle `editor`, auch `admin`).
* Publizieren ist nur möglich, wenn eine URN vergeben ist.
* Publizieren ist nur möglich, wenn eine Lizenz vergeben ist.
* Auch die Übergabe an die Bearbeitung (eingereicht → wird bearbeitet) ist nur möglich,
  wenn eine Lizenz vergeben ist.

### Technische Hinweise

* Die Statusübergänge kommen aus den `x-next`-Labels der Klassifikation `state`
  (`classifications/state.xml`): eingereicht → Bearbeitung/publiziert,
  Bearbeitung → eingereicht/publiziert, publiziert → gesperrt. Die Klassifikation
  muss in der Instanz geladen sein (siehe `setup_vzg.txt`).
* Die Publizier-Regeln (Rolle, URN, Hauptdatei, Lizenz) stecken in `xsl/workflow-util.xsl`
  (überschreibt die MIR-Version von `listStatusChangeOptions`) und gelten damit in
  der Workflow-Box und im Admin-Aktionsmenü. Das ist eine UI-Filterung:
  serverseitig prüft der `MIRStateServlet` nur die ACL und die
  `x-next`-Übergänge, nicht URN, Derivat oder Lizenz.
* Die `mir_licenses`-Klassifikation wird bereits durch das MIR-Standard-Setup geladen
  (`mir-cli/setup-commands.txt`), nicht durch `setup_vzg.txt`.