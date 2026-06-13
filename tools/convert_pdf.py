#!/usr/bin/env python3

import subprocess
import os
import sys
from pathlib import Path

def convert_pdf_to_png():
    """Convert PDFs to PNG using available tools"""
    
    output_dir = Path(__file__).parent / 'visual-verification' / 'screenshots'
    output_dir.mkdir(parents=True, exist_ok=True)
    
    free_pdf = Path(__file__).parent / 'visual-verification' / 'resume-free-user.pdf'
    pro_pdf = Path(__file__).parent / 'visual-verification' / 'resume-pro-user.pdf'
    
    print("=" * 80)
    print("PDF TO PNG CONVERSION")
    print("=" * 80)
    print()
    
    # Try using LibreOffice
    try:
        print("Attempting LibreOffice conversion...")
        subprocess.run([
            'soffice', '--headless', '--convert-to', 'png:writer_pdf_Export',
            '--outdir', str(output_dir),
            str(free_pdf)
        ], check=True, capture_output=True)
        print("✓ LibreOffice conversion successful")
    except Exception as e:
        print(f"⚠ LibreOffice not available: {e}")
    
    # Try using pdftoppm if available
    try:
        print("\nAttempting pdftoppm conversion...")
        subprocess.run([
            'pdftoppm', str(free_pdf),
            str(output_dir / 'free-user-page'),
            '-png', '-r', '150'
        ], check=True, capture_output=True)
        print("✓ pdftoppm conversion successful")
    except Exception as e:
        print(f"⚠ pdftoppm not available: {e}")
    
    # Try using ImageMagick
    try:
        print("\nAttempting ImageMagick conversion...")
        subprocess.run([
            'magick', f'{str(free_pdf)}[0-1]',
            '-quality', '95',
            '-density', '150',
            str(output_dir / 'free-user-page-%d.png')
        ], check=True, capture_output=True)
        print("✓ ImageMagick conversion successful")
    except Exception as e:
        print(f"⚠ ImageMagick not available: {e}")
    
    # Try using Ghostscript
    try:
        print("\nAttempting Ghostscript conversion...")
        for page_num in [1, 2]:
            subprocess.run([
                'gs', '-q', '-dNOPAUSE', '-dBATCH', '-dSAFER',
                '-sDEVICE=pngalpha',
                f'-dFirstPage={page_num}', f'-dLastPage={page_num}',
                '-r150x150',
                f'-sOutputFile={output_dir}/free-user-page-{page_num}.png',
                str(free_pdf)
            ], check=True, capture_output=True)
        
        for page_num in [1, 2]:
            subprocess.run([
                'gs', '-q', '-dNOPAUSE', '-dBATCH', '-dSAFER',
                '-sDEVICE=pngalpha',
                f'-dFirstPage={page_num}', f'-dLastPage={page_num}',
                '-r150x150',
                f'-sOutputFile={output_dir}/pro-user-page-{page_num}.png',
                str(pro_pdf)
            ], check=True, capture_output=True)
        
        print("✓ Ghostscript conversion successful")
    except Exception as e:
        print(f"⚠ Ghostscript not available: {e}")
    
    # Check results
    files = list(output_dir.glob('*.png'))
    if files:
        print(f"\n✓ Screenshots created: {len(files)} files")
        for f in sorted(files):
            print(f"  - {f.name}")
    else:
        print("\n⚠ No PNG files created")

if __name__ == '__main__':
    convert_pdf_to_png()
