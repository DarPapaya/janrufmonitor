Kurzanleitung zum Erzeugen von RSS-jAnrufmonitor-Journals:
=============================================================

1) Installations des HTML/XML-basiertes Journals

2) Kopieren der Dateien footer.jam.html und header.jam.html in das jAnrufmonitor-Datenverzeichnis

3) Konfiguration des HTML/XML-basiertes Journals
	- Dateiname auf *.xml �ndern, z.B. journal.xml
	- Option HTML-Format �ndern in:
 	  <item><title>Anruf von: %callername%</title><description>Neuer Anruf eingegangen um %calltime% von %callername%, %callernumber% auf MSN %msn% (%msnalias%) mit Dienstmerkmal: %cip%</description></item>
	  (Kann selbstverst�ndlich nach Bed�rfnissen angepasst werden)

4) Speichern und einen Anruf simulieren um zu sehen, ob korrekter XML erzeugt wird.

5) journal.xml auf einen Web-Server stellen und mit einem RSS-Reader abonieren.

Viel Spass damit !
Thilo