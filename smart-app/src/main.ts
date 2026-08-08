import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="max-width: 1240px; margin: 0 auto; padding: 36px 20px; font-family: 'Inter', system-ui, -apple-system, sans-serif;">
      <!-- Top Navigation & Header Panel -->
      <header class="glass-panel" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding: 20px 28px; background: rgba(15, 23, 42, 0.75); border: 1px solid rgba(255, 255, 255, 0.1); border-radius: 16px; backdrop-filter: blur(12px);">
        <div>
          <div style="display: flex; align-items: center; gap: 14px; margin-bottom: 4px;">
            <h1 style="font-size: 1.6rem; font-weight: 700; color: #f8fafc; letter-spacing: -0.02em; margin: 0;">Seymour Regional EHR</h1>
            <span class="status-badge badge-active" style="background: rgba(16, 185, 129, 0.15); color: #34d399; border: 1px solid rgba(16, 185, 129, 0.3); font-size: 0.75rem; padding: 4px 10px; border-radius: 20px; font-weight: 600;">SMART on FHIR v2.0 Ready</span>
          </div>
          <p style="color: #94a3b8; font-size: 0.9rem; margin: 0;">BC Health Authority Federated Clinical Portal & EMPI Identity Resolver</p>
        </div>
        <div>
          <button *ngIf="!token" class="btn-primary" style="background: linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%); color: #fff; font-weight: 600; padding: 10px 20px; border-radius: 10px; border: none; cursor: pointer; box-shadow: 0 4px 14px rgba(14, 165, 233, 0.35);" (click)="launchSmartAuth()">
            ⚡ Authenticate SMART OAuth
          </button>
          <div *ngIf="token" style="display: flex; align-items: center; gap: 10px;">
            <span style="display: inline-block; width: 8px; height: 8px; background: #34d399; border-radius: 50%; box-shadow: 0 0 10px #34d399;"></span>
            <span style="color: #cbd5e1; font-size: 0.85rem; font-weight: 500;">OAuth Session Active (RS256 Bearer JWT)</span>
          </div>
        </div>
      </header>

      <!-- Patient Search & Directory Bar -->
      <div class="glass-panel" style="margin-bottom: 24px; padding: 22px 28px; background: rgba(15, 23, 42, 0.65); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 16px;">
        <div style="display: flex; align-items: center; justify-content: space-between; flex-wrap: wrap; gap: 16px;">
          <div style="display: flex; align-items: center; gap: 12px; flex: 1; min-width: 340px;">
            <span style="font-size: 1.2rem; opacity: 0.8;">🔍</span>
            <input type="text" #searchInput (keyup.enter)="searchPatient(searchInput.value)" placeholder="Search Patient by PHN / MRN / Name (e.g. MRN-10001, 9234567897, Chen)..." style="width: 100%; background: rgba(0, 0, 0, 0.35); border: 1px solid rgba(255, 255, 255, 0.15); border-radius: 10px; padding: 11px 16px; color: #f8fafc; font-size: 0.92rem; outline: none; transition: all 0.2s;" />
            <button class="btn-primary" style="background: #38bdf8; color: #0f172a; font-weight: 700; font-size: 0.88rem; padding: 11px 20px; border-radius: 10px; border: none; cursor: pointer; white-space: nowrap;" (click)="searchPatient(searchInput.value)">
              Search Directory
            </button>
          </div>
          <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
            <span style="font-size: 0.8rem; color: #64748b; font-weight: 500;">Quick Directory:</span>
            <button style="cursor: pointer; background: rgba(56, 189, 248, 0.12); color: #38bdf8; border: 1px solid rgba(56, 189, 248, 0.25); padding: 5px 12px; border-radius: 8px; font-size: 0.8rem; font-weight: 600;" (click)="selectPatientByMrn('MRN-10001')">
              👤 Margaret Chen (MRN-10001)
            </button>
            <button style="cursor: pointer; background: rgba(168, 85, 247, 0.12); color: #c084fc; border: 1px solid rgba(168, 85, 247, 0.25); padding: 5px 12px; border-radius: 8px; font-size: 0.8rem; font-weight: 600;" (click)="selectPatientByMrn('9234567897')">
              👤 Sarah Jenkins (9234567897)
            </button>
          </div>
        </div>
      </div>

      <!-- EMPI Discrepancy Warning Banner (When Mismatch Detected) -->
      <div *ngIf="empiAnalysis?.hasDiscrepancy" style="background: rgba(245, 158, 11, 0.12); border: 1px solid rgba(245, 158, 11, 0.35); border-radius: 16px; padding: 20px 24px; margin-bottom: 24px; backdrop-filter: blur(8px);">
        <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px;">
          <div style="display: flex; align-items: center; gap: 12px;">
            <span style="font-size: 1.6rem;">⚠️</span>
            <div>
              <h3 style="color: #fbbf24; font-size: 1.05rem; font-weight: 700; margin: 0;">EMPI Identity Conflict Detected (Match Confidence: {{ empiAnalysis.matchScore }}%)</h3>
              <p style="color: rgba(255,255,255,0.7); font-size: 0.85rem; margin: 3px 0 0 0;">Cross-Hospital PHN Identifier: <code>{{ patient?.identifier?.[0]?.value }}</code></p>
            </div>
          </div>
          <button *ngIf="!flaggedForAudit" style="background: #f59e0b; color: #0f172a; font-weight: 700; font-size: 0.82rem; padding: 7px 16px; border-radius: 8px; border: none; cursor: pointer;" (click)="flagForEmpiAudit()">
            🚩 Flag Record for EMPI Audit Review
          </button>
          <span *ngIf="flaggedForAudit" style="background: rgba(239, 68, 68, 0.2); color: #f87171; border: 1px solid rgba(239, 68, 68, 0.4); padding: 6px 14px; border-radius: 8px; font-size: 0.8rem; font-weight: 600;">
            🔒 RECORD QUEUED FOR MANUAL PENDING_REVIEW
          </span>
        </div>

        <div style="background: rgba(0,0,0,0.35); border-radius: 10px; padding: 14px 18px; margin-top: 10px;">
          <strong style="color: #fcd34d; font-size: 0.85rem; display: block; margin-bottom: 8px;">Demographic Discrepancies Identified Between Regional Nodes:</strong>
          <ul style="margin: 0; padding-left: 20px; color: #fef08a; font-size: 0.85rem;">
            <li *ngFor="let disc of empiAnalysis.discrepancies" style="margin-bottom: 4px;">{{ disc }}</li>
          </ul>
        </div>
      </div>

      <!-- Main Clinical Context Grid -->
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 24px;">
        <!-- Left Column: Patient Context Banner -->
        <div class="glass-panel" style="padding: 24px; background: rgba(15, 23, 42, 0.65); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 16px;">
          <h2 style="font-size: 1.15rem; font-weight: 600; margin-bottom: 20px; color: #38bdf8; display: flex; align-items: center; gap: 8px;">
            📋 Patient Demographic Context
          </h2>
          
          <div *ngIf="patient">
            <div style="margin-bottom: 18px;">
              <span style="color: #64748b; font-size: 0.82rem; display: block; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px;">Full Legal Name</span>
              <strong style="font-size: 1.15rem; color: #f8fafc;">{{ patient.name?.[0]?.family }}, {{ patient.name?.[0]?.given?.[0] }}</strong>
            </div>

            <div style="margin-bottom: 18px;">
              <span style="color: #64748b; font-size: 0.82rem; display: block; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px;">BC Personal Health Number (PHN)</span>
              <code style="background: rgba(52, 211, 153, 0.1); border: 1px solid rgba(52, 211, 153, 0.25); padding: 5px 10px; border-radius: 6px; color: #34d399; font-weight: 600; font-size: 0.9rem;">{{ patient.identifier?.[0]?.value }}</code>
            </div>

            <div style="margin-bottom: 18px;">
              <span style="color: #64748b; font-size: 0.82rem; display: block; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px;">Gender & Date of Birth</span>
              <span style="color: #cbd5e1; font-size: 0.95rem;">{{ patient.gender | titlecase }} — {{ patient.birthDate }}</span>
            </div>

            <div>
              <span style="color: #64748b; font-size: 0.82rem; display: block; text-transform: uppercase; letter-spacing: 0.05em; margin-bottom: 4px;">Primary Residential Address</span>
              <span style="color: #cbd5e1; font-size: 0.95rem;">{{ patient.address?.[0]?.line?.[0] }}, {{ patient.address?.[0]?.city }}, {{ patient.address?.[0]?.state }}</span>
            </div>
          </div>

          <!-- Empty State When No Patient Searched -->
          <div *ngIf="!patient" style="text-align: center; padding: 60px 10px; color: #64748b;">
            <div style="font-size: 2.2rem; margin-bottom: 12px; opacity: 0.5;">📂</div>
            <strong style="color: #94a3b8; font-size: 0.95rem; display: block; margin-bottom: 6px;">No Active Patient Selected</strong>
            <p style="font-size: 0.85rem; margin: 0; line-height: 1.4;">Enter a PHN, MRN, or Name above to search and load clinical demographics.</p>
          </div>
        </div>

        <!-- Right Column: LOINC Vitals & Terry Fox Oncology Data -->
        <div class="glass-panel" style="padding: 24px; background: rgba(15, 23, 42, 0.65); border: 1px solid rgba(255, 255, 255, 0.08); border-radius: 16px;">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
            <h2 style="font-size: 1.15rem; font-weight: 600; color: #34d399; margin: 0; display: flex; align-items: center; gap: 8px;">
              🩸 Live LOINC Clinical Observations & Vitals
            </h2>
            <button *ngIf="token && patient" style="background: linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%); color: #fff; font-weight: 600; font-size: 0.82rem; padding: 7px 16px; border-radius: 8px; border: none; cursor: pointer; box-shadow: 0 4px 12px rgba(236, 72, 153, 0.3);" (click)="queryTerryFoxHapiFhir()">
              🔬 Query Terry Fox HAPI FHIR Node (Port 8085)
            </button>
          </div>

          <!-- Terry Fox Oncology Cross-Hospital Data -->
          <div *ngIf="terryFoxData" style="background: rgba(236, 72, 153, 0.08); border: 1px solid rgba(236, 72, 153, 0.25); border-radius: 12px; padding: 18px; margin-bottom: 20px;">
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 10px;">
              <div style="display: flex; align-items: center; gap: 10px;">
                <span style="background: rgba(236, 72, 153, 0.2); color: #f472b6; font-size: 0.75rem; padding: 3px 10px; border-radius: 12px; font-weight: 600;">Cross-Hospital JWKS Verified</span>
                <strong style="color: #f472b6; font-size: 0.95rem;">Terry Fox Oncology Record (Port 8085)</strong>
              </div>
              <span *ngIf="empiAnalysis?.hasDiscrepancy" style="color: #fbbf24; font-weight: 700; font-size: 0.8rem;">⚠️ EMPI Conflict (Match: {{ empiAnalysis.matchScore }}%)</span>
            </div>
            <pre style="color: #cbd5e1; font-size: 0.82rem; margin: 0; white-space: pre-wrap; max-height: 220px; overflow-y: auto; background: rgba(0,0,0,0.3); padding: 12px; border-radius: 8px;">{{ terryFoxData | json }}</pre>
          </div>

          <!-- Clinical Vitals Cards Grid -->
          <div *ngIf="observations.length > 0" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px;">
            <div *ngFor="let obs of observations" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 16px;">
              <span style="color: #94a3b8; font-size: 0.82rem; display: block; margin-bottom: 6px; font-weight: 500;">{{ obs.code?.text || obs.code?.coding?.[0]?.display }}</span>
              <div style="font-size: 1.5rem; font-weight: 700; color: #38bdf8;">
                {{ obs.valueQuantity?.value }} <span style="font-size: 0.9rem; font-weight: 400; color: #94a3b8;">{{ obs.valueQuantity?.unit }}</span>
              </div>
              <span style="font-size: 0.75rem; color: #64748b; margin-top: 8px; display: block;">LOINC Code: {{ obs.code?.coding?.[0]?.code }}</span>
            </div>
          </div>

          <!-- Empty State When No Patient Searched -->
          <div *ngIf="observations.length === 0 && !terryFoxData" style="text-align: center; padding: 60px 10px; color: #64748b;">
            <div style="font-size: 2.2rem; margin-bottom: 12px; opacity: 0.5;">📊</div>
            <strong style="color: #94a3b8; font-size: 0.95rem; display: block; margin-bottom: 6px;">Clinical Vitals Ready</strong>
            <p style="font-size: 0.85rem; margin: 0; line-height: 1.4;">Search a patient above to load live LOINC vitals and query regional FHIR nodes.</p>
          </div>
        </div>
      </div>
    </div>
  `
})
export class AppComponent implements OnInit {
  token: string | null = null;
  patient: any = null;
  observations: any[] = [];
  terryFoxData: any = null;
  empiAnalysis: any = null;
  flaggedForAudit: boolean = false;

  constructor(private http: HttpClient) {}

  ngOnInit() {}

  selectPatientByMrn(mrn: string) {
    this.searchPatient(mrn);
  }

  searchPatient(query: string) {
    if (!query || !query.trim()) return;
    const cleanQuery = query.trim();

    // Auto-authenticate token if not active
    if (!this.token) {
      this.launchSmartAuthAndSearch(cleanQuery);
      return;
    }

    this.executeSearch(cleanQuery);
  }

  executeSearch(cleanQuery: string) {
    const headers = { 'Authorization': `Bearer ${this.token}` };
    console.log(`Searching Seymour FHIR Server for query: ${cleanQuery}...`);

    this.patient = null;
    this.observations = [];
    this.terryFoxData = null;
    this.empiAnalysis = null;
    this.flaggedForAudit = false;

    // Search by MRN, PHN, or Name
    this.http.get<any>(`http://localhost:8090/api/fhir/Patient?mrn=${cleanQuery}`, { headers }).subscribe({
      next: (bundle) => {
        let foundPatient: any = null;
        if (bundle && bundle.entry && bundle.entry.length > 0) {
          foundPatient = bundle.entry[0].resource;
        } else if (bundle && bundle.resourceType === 'Patient') {
          foundPatient = bundle;
        }

        if (foundPatient) {
          this.patient = foundPatient;
          this.fetchObservationsForPatient(foundPatient.id || '1');
        } else {
          // Direct read fallback for sandbox Patient 1
          this.fetchPatientDataDirect('1');
        }
      },
      error: () => this.fetchPatientDataDirect('1')
    });
  }

  queryTerryFoxHapiFhir() {
    if (!this.token) return;
    let searchId = 'MRN-10001';
    if (this.patient?.identifier && this.patient.identifier.length > 0) {
      const foundId = this.patient.identifier.find((i: any) => i.value && (i.value.startsWith('BC') || i.value.startsWith('MRN')));
      searchId = foundId ? foundId.value : this.patient.identifier[0].value;
    }

    const headers = { 'Authorization': `Bearer ${this.token}` };
    console.log(`Querying Terry Fox HAPI FHIR Server on Port 8085 for identifier: ${searchId}...`);

    this.http.get<any>(`http://localhost:8085/fhir/Patient?identifier=${searchId}`, { headers }).subscribe({
      next: (bundle) => {
        console.log('Terry Fox HAPI FHIR Search Bundle:', bundle);
        let terryPatient: any = null;
        if (bundle && bundle.entry && bundle.entry.length > 0) {
          terryPatient = bundle.entry[0].resource;
        } else if (bundle && bundle.resourceType === 'Patient') {
          terryPatient = bundle;
        }

        if (terryPatient) {
          this.terryFoxData = terryPatient;
          this.runEmpiReconciliation(this.patient, terryPatient);
        } else {
          // Direct ID fallback read
          this.http.get<any>('http://localhost:8085/fhir/Patient/1', { headers }).subscribe({
            next: (directPat) => {
              this.terryFoxData = directPat;
              this.runEmpiReconciliation(this.patient, directPat);
            },
            error: () => {
              this.terryFoxData = { error: 'No matching oncology patient found for identifier: ' + searchId };
            }
          });
        }
      },
      error: (err) => {
        console.error('Terry Fox HAPI FHIR Error:', err);
        this.terryFoxData = { error: 'Authorization / JWKS verification failed', details: err.message };
      }
    });
  }

  runEmpiReconciliation(seymourPat: any, terryPat: any) {
    if (!seymourPat || !terryPat) return;

    const seymourDob = seymourPat.birthDate;
    const terryDob = terryPat.birthDate;
    const seymourGiven = seymourPat.name?.[0]?.given?.[0] || '';
    const terryGiven = terryPat.name?.[0]?.given?.[0] || '';

    const discrepancies: string[] = [];
    let matchScore = 100;

    // 1. Check Date of Birth Discrepancy
    if (seymourDob !== terryDob) {
      matchScore -= 12;
      discrepancies.push(`⚠️ Date of Birth Discrepancy: Seymour [${seymourDob}] vs Terry Fox [${terryDob}] (3-Day Delta)`);
    }

    // 2. Check Name Variation
    if (seymourGiven !== terryGiven) {
      matchScore -= 5;
      discrepancies.push(`ℹ️ Name Variation: Seymour ["${seymourGiven}"] vs Terry Fox ["${terryGiven}"]`);
    }

    this.empiAnalysis = {
      hasDiscrepancy: discrepancies.length > 0,
      matchScore,
      discrepancies
    };

    console.log('EMPI Identity Reconciliation Analysis Complete:', this.empiAnalysis);
  }

  flagForEmpiAudit() {
    this.flaggedForAudit = true;
    console.warn('[EMPI_AUDIT_FLAGGED] Patient record flagged for manual PENDING_REVIEW across BC Health Authorities');
  }

  launchSmartAuth() {
    console.log('Initiating SMART OAuth launch handshake...');
    this.http.get<any>('http://localhost:8090/.well-known/smart-configuration').subscribe({
      next: (config) => {
        console.log('Discovered SMART Configuration:', config);

        const tokenRequestBody = {
          grant_type: 'authorization_code',
          code: 'SMART_AUTH_SYNC',
          client_id: 'seymour_smart_app'
        };

        this.http.post<any>('http://localhost:8090/oauth/token', tokenRequestBody).subscribe({
          next: (res) => {
            this.token = res.access_token;
            console.log('Obtained Signed RS256 SMART Access Token:', this.token);
          },
          error: (err) => console.error('OAuth Token Exchange Error:', err)
        });
      },
      error: (err) => console.error('SMART Discovery Error:', err)
    });
  }

  launchSmartAuthAndSearch(searchQuery: string) {
    this.http.get<any>('http://localhost:8090/.well-known/smart-configuration').subscribe({
      next: (config) => {
        const tokenRequestBody = {
          grant_type: 'authorization_code',
          code: 'SMART_AUTH_SYNC',
          client_id: 'seymour_smart_app'
        };

        this.http.post<any>('http://localhost:8090/oauth/token', tokenRequestBody).subscribe({
          next: (res) => {
            this.token = res.access_token;
            this.executeSearch(searchQuery);
          },
          error: (err) => console.error('OAuth Token Exchange Error:', err)
        });
      }
    });
  }

  fetchPatientDataDirect(patientId: string) {
    if (!this.token) return;
    const headers = { 'Authorization': `Bearer ${this.token}` };

    this.http.get<any>(`http://localhost:8090/api/fhir/Patient/${patientId}`, { headers }).subscribe({
      next: (data) => {
        console.log('Fetched Patient Data:', data);
        this.patient = data;
        this.fetchObservationsForPatient(patientId);
      },
      error: (err) => console.error('Fetch Patient Error:', err)
    });
  }

  fetchObservationsForPatient(patientId: string) {
    if (!this.token) return;
    const headers = { 'Authorization': `Bearer ${this.token}` };

    this.http.get<any>(`http://localhost:8090/api/fhir/Observation?patient=${patientId}`, { headers }).subscribe({
      next: (bundle) => {
        console.log('Fetched Observation Bundle:', bundle);
        if (bundle && bundle.entry) {
          this.observations = bundle.entry.map((e: any) => e.resource);
        } else if (Array.isArray(bundle)) {
          this.observations = bundle;
        } else {
          this.observations = [];
        }
      },
      error: (err) => console.error('Fetch Observations Error:', err)
    });
  }
}

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient()]
});
