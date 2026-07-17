-- Which tool produced a rendition (R-1 observability): 'upload' for the
-- original, else the conversion service's producer — ocrmypdf | ghostscript |
-- libreoffice | passthrough. 'passthrough' marks a PDF stored unnormalized
-- because no PDF/A toolchain was available; existing rows stay NULL (unknown).
ALTER TABLE rendition ADD COLUMN producer TEXT;
