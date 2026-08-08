import { bootstrapApplication } from '@angular/platform-browser';
import { provideHttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { HttpClient } from '@angular/common/http';
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
        <button *if="!token" class="btn-primary" (click)="launchSmartAuth()">⚡ Launch SMART OAuth Handshake</button>
        <div *if="token" class="status-badge badge-active">Bearer Token Active (RS256 JWT)</div>
      </header>

      <!-- Main Grid Layout -->
      <div style="display: grid; grid-template-columns: 1fr 2fr; gap: 24px;">
        <!-- Left Column: Patient Context Banner -->
        <div class="glass-panel">
          <h2 style="font-size: 1.2rem; font-weight: 600; margin-bottom: 20px; color: var(--accent-cyan);">📋 Patient Demographic Context</h2>
          
          <div *if="patient">
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

          <div *if="!patient" style="text-align: center; padding: 40px 0; color: var(--text-sub);">
            <p>No active launch patient context.</p>
            <p style="font-size: 0.85rem; margin-top: 8px;">Click "Launch SMART OAuth Handshake" above to authenticate.</p>
          </div>
        </div>

        <!-- Right Column: Clinical Vitals & Lab Observations -->
        <div class="glass-panel">
          <h2 style="font-size: 1.2rem; font-weight: 600; margin-bottom: 20px; color: var(--accent-emerald);">🩸 Live LOINC Clinical Observations & Vitals</h2>

          <div *if="observations.length > 0" style="display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px;">
            <div *for="let obs of observations" style="background: rgba(255,255,255,0.03); border: 1px solid rgba(255,255,255,0.06); border-radius: 12px; padding: 16px;">
              <span style="color: var(--text-sub); font-size: 0.85rem; display: block; margin-bottom: 6px;">{{ obs.code?.text || obs.code?.coding?.[0]?.display }}</span>
              <div style="font-size: 1.5rem; font-weight: 700; color: var(--accent-cyan);">
                {{ obs.valueQuantity?.value }} <span style="font-size: 0.9rem; font-weight: 400; color: var(--text-sub);">{{ obs.valueQuantity?.unit }}</span>
              </div>
              <span style="font-size: 0.75rem; color: var(--text-sub); margin-top: 8px; display: block;">LOINC Code: {{ obs.code?.coding?.[0]?.code }}</span>
            </div>
          </div>

          <div *if="observations.length === 0" style="text-align: center; padding: 60px 0; color: var(--text-sub);">
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

  constructor(private http: HttpClient) {}

  ngOnInit() {}

  launchSmartAuth() {
    // 1. SMART Discovery Request
    this.http.get<any>('http://localhost:8090/.well-known/smart-configuration').subscribe({
      next: (config) => {
        console.log('Discovered SMART Configuration:', config);

        // 2. Obtain Token via OAuth Token Endpoint
        const payload = new URLSearchParams();
        payload.set('grant_type', 'authorization_code');
        payload.set('code', 'SMART_AUTH_SYNC');
        payload.set('client_id', 'seymour_smart_app');

        this.http.post<any>('http://localhost:8090/oauth/token', payload.toString(), {
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' }
        }).subscribe({
          next: (res) => {
            this.token = res.access_token;
            console.log('Obtained Signed RS256 SMART Access Token:', this.token);
            
            // 3. Query Patient Resource with Bearer Token
            this.fetchPatientData(res.patient, this.token!);
          }
        });
      }
    });
  }

  fetchPatientData(patientId: string, token: string) {
    const headers = { 'Authorization': `Bearer ${token}` };

    // Fetch Patient Demographic Context
    this.http.get<any>(`http://localhost:8090/api/fhir/Patient/${patientId}`, { headers }).subscribe({
      next: (data) => this.patient = data
    });

    // Fetch LOINC Vitals & Clinical Observations
    this.http.get<any>(`http://localhost:8090/api/fhir/Observation?patient=${patientId}`, { headers }).subscribe({
      next: (bundle) => {
        if (bundle.entry) {
          this.observations = bundle.entry.map((e: any) => e.resource);
        }
      }
    });
  }
}

bootstrapApplication(AppComponent, {
  providers: [provideHttpClient()]
});
