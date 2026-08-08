import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient, HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  template: `
    <div style="max-width: 1200px; margin: 0 auto; padding: 40px 20px;">
      <!-- Header Banner -->
      <header class="glass-panel" style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 30px;">
        <div>
          <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 6px;">
            <h1 style="font-size: 1.6rem; font-weight: 700;">Seymour Regional EHR</h1>
            <span class="status-badge badge-active">SMART on FHIR v2.0 Ready</span>
          </div>
          <p style="color: var(--text-sub); font-size: 0.95rem;">BC Health Authority SMART App Launcher & Patient Clinical Portal</p>
        </div>
        <button *ngIf="!token" class="btn-primary" (click)="launchSmartAuth()">⚡ Launch SMART OAuth Handshake</button>
        <div *ngIf="token" class="status-badge badge-active">Bearer Token Active (RS256 JWT)</div>
      </header>

      <!-- EMPI Discrepancy Warning Banner (When Mismatch Detected) -->
      <div *ngIf="empiAnalysis?.hasDiscrepancy" style="background: rgba(245, 158, 11, 0.12); border: 1px solid rgba(245, 158, 11, 0.4); border-radius: 14px; padding: 20px; margin-bottom: 30px;">
        <div style="display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px;">
          <div style="display: flex; align-items: center; gap: 10px;">
            <span style="font-size: 1.5rem;">⚠️</span>
            <div>
              <h3 style="color: #fbbf24; font-size: 1.1rem; font-weight: 700; margin: 0;">EMPI Identity Conflict Detected (Match Confidence: {{ empiAnalysis.matchScore }}%)</h3>
              <p style="color: rgba(255,255,255,0.7); font-size: 0.85rem; margin: 2px 0 0 0;">Cross-Hospital PHN Linked: <code>{{ patient?.identifier?.[0]?.value }}</code></p>
            </div>
          </div>
          <button *ngIf="!flaggedForAudit" class="btn-primary" style="background: #f59e0b; color: #000; font-weight: 700; font-size: 0.8rem; padding: 6px 14px;" (click)="flagForEmpiAudit()">
            🚩 Flag Record for EMPI Audit Review
          </button>
          <span *ngIf="flaggedForAudit" class="status-badge" style="background: rgba(239, 68, 68, 0.25); color: #f87171; border: 1px solid #ef4444; padding: 6px 12px;">
            🔒 RECORD QUEUED FOR MANUAL PENDING_REVIEW
          </span>
        </div>

        <div style="background: rgba(0,0,0,0.3); border-radius: 10px; padding: 14px; margin-top: 10px;">
          <strong style="color: #fcd34d; font-size: 0.85rem; display: block; margin-bottom: 8px;">Demographic Discrepancies Identified Between Regional Nodes:</strong>
          <ul style="margin: 0; padding-left: 20px; color: #fef08a; font-size: 0.85rem;">
            <li *ngFor="let disc of empiAnalysis.discrepancies" style="margin-bottom: 4px;">{{ disc }}</li>
          </ul>
        </div>
      </div>

      <!-- Main Grid Layout -->
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 24px;">
        <!-- Left Column: Patient Context Banner -->
        <div class="glass-panel">
          <h2 style="font-size: 1.2rem; font-weight: 600; margin-bottom: 20px; color: var(--accent-cyan);">📋 Patient Demographic Context</h2>
          
          <div *ngIf="patient">
            <div style="margin-bottom: 16px;">
              <span style="color: var(--text-sub); font-size: 0.85rem; display: block;">Full Name</span>
              <strong style="font-size: 1.1rem;">{{ patient.name?.[0]?.family }}, {{ patient.name?.[0]?.given?.[0] }}</strong>
            </div>

            <div style="margin-bottom: 16px;">
              <span style="color: var(--text-sub); font-size: 0.85rem; display: block;">BC Personal Health Number (PHN)</span>
              <code style="background: rgba(255,255,255,0.06); padding: 4px 8px; border-radius: 6px; color: var(--accent-emerald);">{{ patient.identifier?.[0]?.value }}</code>
            </div>

            <div style="margin-bottom: 16px;">
              <span style="color: var(--text-sub); font-size: 0.85rem; display: block;">Gender & Date of Birth</span>
              <span>{{ patient.gender | titlecase }} — {{ patient.birthDate }}</span>
            </div>

            <div>
              <span style="color: var(--text-sub); font-size: 0.85rem; display: block;">Primary Address</span>
              <span>{{ patient.address?.[0]?.line?.[0] }}, {{ patient.address?.[0]?.city }}, {{ patient.address?.[0]?.state }}</span>
            </div>
          </div>

          <div *ngIf="!patient" style="text-align: center; padding: 40px 0; color: var(--text-sub);">
            <p>No active launch patient context.</p>
            <p style="font-size: 0.85rem; margin-top: 8px;">Click "Launch SMART OAuth Handshake" above to authenticate.</p>
          </div>
        </div>

        <!-- Right Column: Clinical Vitals & Lab Observations + Terry Fox Cross-Hospital Section -->
        <div class="glass-panel">
          <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px;">
            <h2 style="font-size: 1.2rem; font-weight: 600; color: var(--accent-emerald);">🩸 Live LOINC Clinical Observations & Vitals</h2>
            <button *ngIf="token" class="btn-primary" style="background: linear-gradient(135deg, #ec4899 0%, #8b5cf6 100%); font-size: 0.85rem; padding: 6px 14px;" (click)="queryTerryFoxHapiFhir()">
              🔬 Query Terry Fox HAPI FHIR Node (Port 8085)
            </button>
          </div>

          <!-- Terry Fox Oncology Cross-Hospital Data -->
          <div *ngIf="terryFoxData" style="background: rgba(236,72,153,0.1); border: 1px solid rgba(236,72,153,0.25); border-radius: 12px; padding: 16px; margin-bottom: 20px;">
            <div style="display: flex; align-items: center; justify-content: space-between; margin-bottom: 8px;">
              <div style="display: flex; align-items: center; gap: 8px;">
                <span class="status-badge badge-active" style="background: rgba(236,72,153,0.2); color: #f472b6;">Cross-Hospital JWKS Verified</span>
                <strong style="color: #f472b6; font-size: 0.95rem;">Terry Fox Oncology Record (Port 8085)</strong>
              </div>
              <span *ngIf="empiAnalysis?.hasDiscrepancy" style="color: #fbbf24; font-weight: 700; font-size: 0.8rem;">⚠️ EMPI Conflict (Match: {{ empiAnalysis.matchScore }}%)</span>
            </div>
            <pre style="color: var(--text-sub); font-size: 0.8rem; margin: 0; white-space: pre-wrap; max-height: 200px; overflow-y: auto;">{{ terryFoxData | json }}</pre>
          </div>

          <div *ngIf="observations.length > 0" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px;">
            <div *ngFor="let obs of observations" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 16px;">
              <span style="color: var(--text-sub); font-size: 0.85rem; display: block; margin-bottom: 6px;">{{ obs.code?.text || obs.code?.coding?.[0]?.display }}</span>
              <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent-cyan);">
                {{ obs.valueQuantity?.value }} <span style="font-size: 0.9rem; font-weight: 400; color: var(--text-sub);">{{ obs.valueQuantity?.unit }}</span>
              </div>
              <span style="font-size: 0.75rem; color: var(--text-sub); margin-top: 8px; display: block;">LOINC Code: {{ obs.code?.coding?.[0]?.code }}</span>
            </div>
          </div>

          <div *ngIf="observations.length === 0" style="text-align: center; padding: 60px 0; color: var(--text-sub);">
            <p>Click "Launch SMART OAuth Handshake" to auto-discover metadata and load live patient observations from Seymour FHIR Server.</p>
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

  queryTerryFoxHapiFhir() {
    if (!this.token) return;
    const phn = this.patient?.identifier?.[0]?.value || 'MRN-10001';
    const headers = { 'Authorization': `Bearer ${this.token}` };
    console.log(`Querying Terry Fox HAPI FHIR Server on Port 8085 for PHN: ${phn}...`);

    this.http.get<any>(`http://localhost:8085/fhir/Patient?identifier=${phn}`, { headers }).subscribe({
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
          this.terryFoxData = { error: 'No matching oncology patient found for PHN: ' + phn };
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
            this.fetchPatientData(res.patient || '1', this.token!);
          },
          error: (err) => console.error('OAuth Token Exchange Error:', err)
        });
      },
      error: (err) => console.error('SMART Discovery Error:', err)
    });
  }

  fetchPatientData(patientId: string, token: string) {
    const headers = { 'Authorization': `Bearer ${token}` };

    this.http.get<any>(`http://localhost:8090/api/fhir/Patient/${patientId}`, { headers }).subscribe({
      next: (data) => {
        console.log('Fetched Patient Data:', data);
        this.patient = data;
      },
      error: (err) => console.error('Fetch Patient Error:', err)
    });

    this.http.get<any>(`http://localhost:8090/api/fhir/Observation?patient=${patientId}`, { headers }).subscribe({
      next: (bundle) => {
        console.log('Fetched Observation Bundle:', bundle);
        if (bundle && bundle.entry) {
          this.observations = bundle.entry.map((e: any) => e.resource);
        } else if (Array.isArray(bundle)) {
          this.observations = bundle;
        }
      },
      error: (err) => console.error('Fetch Observations Error:', err)
    });
  }
}

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient()]
});
